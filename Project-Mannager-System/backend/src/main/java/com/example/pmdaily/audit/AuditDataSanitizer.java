package com.example.pmdaily.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Che field nhạy cảm trước khi lưu JSONB vào audit_logs
 * (docs/design/06-logging-audit-design.md muc 5): không bao giờ lưu password/token/secret.
 */
@Component
public class AuditDataSanitizer {

    private static final List<String> SENSITIVE_KEY_MARKERS = List.of(
            "password", "token", "secret", "authorization", "credential");

    private static final int MAX_DEPTH = 5;
    private static final int MAX_ENTRIES = 500;

    public Map<String, Object> sanitize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return sanitizeMap(data, 0);
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> data, int depth) {
        if (depth > MAX_DEPTH) {
            return Map.of("__truncated__", true);
        }
        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            if (count >= MAX_ENTRIES) {
                result.put("__truncated__", true);
                break;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isSensitive(key)) {
                result.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                result.put(key, sanitizeMap(nested, depth + 1));
            } else if (value instanceof List<?> list) {
                result.put(key, sanitizeList(list, depth + 1));
            } else {
                result.put(key, value);
            }
            count++;
        }
        return result;
    }

    private Object sanitizeList(List<?> list, int depth) {
        if (depth > MAX_DEPTH) {
            return List.of("__truncated__");
        }
        return list.stream()
                .map(item -> item instanceof Map<?, ?> m
                        ? sanitizeMap(m, depth + 1)
                        : item)
                .toList();
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_MARKERS.stream().anyMatch(lower::contains);
    }
}
