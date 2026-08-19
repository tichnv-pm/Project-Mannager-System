package com.example.pmdaily.exception;

import com.example.pmdaily.common.ErrorCode;

/**
 * Không tìm thấy đối tượng → HTTP 404, code NOT_FOUND (docs/design/05 muc 2).
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String entityName, Object id) {
        super(ErrorCode.NOT_FOUND,
                "Không tìm thấy " + entityName + (id != null ? " " + id : ""));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
