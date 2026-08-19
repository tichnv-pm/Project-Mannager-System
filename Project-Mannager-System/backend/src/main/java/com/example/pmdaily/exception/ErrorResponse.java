package com.example.pmdaily.exception;

import java.time.Instant;
import java.util.List;

/**
 * Error response thống nhất (docs/design/05-error-handling-design.md muc 1).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldErrorItem> fieldErrors,
        String traceId) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private int status;
        private String error;
        private String code;
        private String message;
        private String path;
        private List<FieldErrorItem> fieldErrors = List.of();
        private String traceId;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder fieldErrors(List<FieldErrorItem> fieldErrors) {
            this.fieldErrors = fieldErrors;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(timestamp, status, error, code, message, path, fieldErrors, traceId);
        }
    }
}
