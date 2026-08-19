package com.example.pmdaily.exception;

import com.example.pmdaily.common.ErrorCode;

/**
 * Xung đột dữ liệu (optimistic lock, duplicate) → HTTP 409 (docs/design/05 muc 2).
 */
public class ConflictException extends BusinessException {

    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
