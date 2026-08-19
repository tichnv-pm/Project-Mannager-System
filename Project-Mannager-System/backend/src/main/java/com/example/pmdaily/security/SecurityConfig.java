package com.example.pmdaily.security;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Security filter chain (docs/design/04-security-design.md).
 * - Stateless (JWT trong header Authorization, không cookie → không cần CSRF — csrf.disable()).
 * - Public: login/refresh, Swagger UI, /v3/api-docs, actuator health.
 * - 401/403 trả JSON error response thống nhất.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/api/v1/public/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(request, response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(request, response, ErrorCode.ACCESS_DENIED)))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .status(errorCode.getStatus().value())
                .error(errorName(errorCode.getStatus()))
                .code(errorCode.name())
                .message(errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .fieldErrors(List.of())
                .traceId(resolveTraceId())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static String errorName(HttpStatus status) {
        String reason = status.getReasonPhrase();
        return reason != null ? reason.toUpperCase().replace(' ', '_') : status.name();
    }

    private static String resolveTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId != null ? traceId : java.util.UUID.randomUUID().toString();
    }
}
