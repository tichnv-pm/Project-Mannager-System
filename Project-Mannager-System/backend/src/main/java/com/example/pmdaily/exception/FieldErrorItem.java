package com.example.pmdaily.exception;

/**
 * Lỗi theo field (bean validation) — docs/design/05-error-handling-design.md.
 */
public record FieldErrorItem(String field, String message) {
}
