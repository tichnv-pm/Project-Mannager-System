package com.example.pmdaily.exception;

import com.example.pmdaily.common.ErrorCode;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ có mã chuẩn (catalog: docs/design/05-error-handling-design.md muc 3).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
