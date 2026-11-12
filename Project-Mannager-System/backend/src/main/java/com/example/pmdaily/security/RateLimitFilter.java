package com.example.pmdaily.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limit theo IP cho các endpoint public nhạy cảm (không cần JWT nên không
 * được bảo vệ bởi cơ chế auth): login, git webhook, và share-link báo cáo stakeholder
 * (GĐ4 cải tiến — chống dò token qua endpoint public). Sliding window đơn giản
 * trong bộ nhớ (đủ cho triển khai 1 instance hiện tại — docker-compose); nếu
 * scale ra nhiều instance cần chuyển sang store dùng chung (Redis...).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private record Rule(String pathPrefix, int maxRequests, long windowMillis) {
    }

    private record Counter(AtomicInteger count, AtomicLong windowStart) {
    }

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final List<Rule> rules;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.login.max-requests:20}") int loginMaxRequests,
            @Value("${app.security.rate-limit.login.window-seconds:60}") int loginWindowSeconds,
            @Value("${app.security.rate-limit.webhook.max-requests:60}") int webhookMaxRequests,
            @Value("${app.security.rate-limit.webhook.window-seconds:60}") int webhookWindowSeconds,
            @Value("${app.security.rate-limit.shared-report.max-requests:30}") int sharedReportMaxRequests,
            @Value("${app.security.rate-limit.shared-report.window-seconds:60}") int sharedReportWindowSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.rules = List.of(
                new Rule("/api/v1/auth/login", loginMaxRequests, loginWindowSeconds * 1000L),
                new Rule("/api/v1/public/webhooks/", webhookMaxRequests, webhookWindowSeconds * 1000L),
                new Rule("/api/v1/public/shared-reports/", sharedReportMaxRequests, sharedReportWindowSeconds * 1000L));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Rule matched = rules.stream()
                .filter(r -> path.startsWith(r.pathPrefix()))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = matched.pathPrefix() + "|" + clientIp(request);
        if (isOverLimit(key, matched)) {
            log.warn("Rate limit exceeded path={} ip={}", path, clientIp(request));
            writeRateLimitError(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOverLimit(String key, Rule rule) {
        long now = System.currentTimeMillis();
        Counter counter = counters.computeIfAbsent(key,
                k -> new Counter(new AtomicInteger(0), new AtomicLong(now)));

        synchronized (counter) {
            if (now - counter.windowStart().get() >= rule.windowMillis()) {
                counter.windowStart().set(now);
                counter.count().set(0);
            }
            return counter.count().incrementAndGet() > rule.maxRequests();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(errorCode.getStatus().value())
                .error("TOO_MANY_REQUESTS")
                .code(errorCode.name())
                .message(errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .fieldErrors(List.of())
                .traceId(java.util.UUID.randomUUID().toString())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
