package com.example.pmdaily.audit;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDataSanitizerTest {

    private final AuditDataSanitizer sanitizer = new AuditDataSanitizer();

    @Test
    void sensitiveKeys_areMasked() {
        Map<String, Object> result = sanitizer.sanitize(Map.of(
                "title", "Fix lỗi login",
                "password", "Admin@123",
                "token", "abc.def.ghi",
                "jwt_secret", "s3cret"));

        assertThat(result.get("title")).isEqualTo("Fix lỗi login");
        assertThat(result.get("password")).isEqualTo("***");
        assertThat(result.get("token")).isEqualTo("***");
        assertThat(result.get("jwt_secret")).isEqualTo("***");
    }

    @Test
    void nestedMaps_areSanitizedRecursively() {
        Map<String, Object> result = sanitizer.sanitize(Map.of(
                "assignee", Map.of("fullName", "Lan", "passwordHash", "xxx")));

        assertThat(((Map<?, ?>) result.get("assignee")).get("fullName")).isEqualTo("Lan");
        assertThat(((Map<?, ?>) result.get("assignee")).get("passwordHash")).isEqualTo("***");
    }

    @Test
    void sensitiveInsideList_areMasked() {
        Map<String, Object> result = sanitizer.sanitize(Map.of(
                "items", List.of(Map.of("token", "plain"), "ok")));

        assertThat(((List<?>) result.get("items")).get(0))
                .isEqualTo(Map.of("token", "***"));
    }

    @Test
    void nullOrEmpty_returnsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize(Map.of())).isNull();
    }

    @Test
    void deepNesting_isTruncated() {
        Map<String, Object> deep = Map.of("level1", Map.of("level2", Map.of(
                "level3", Map.of("level4", Map.of("level5", Map.of("level6", "too deep"))))));
        Map<String, Object> result = sanitizer.sanitize(deep);
        assertThat(result).isNotNull();
    }
}
