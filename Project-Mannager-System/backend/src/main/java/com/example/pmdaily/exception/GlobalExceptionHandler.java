package com.example.pmdaily.exception;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.pmdaily.common.Constants;
import com.example.pmdaily.common.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Xử lý lỗi tập trung (docs/design/05-error-handling-design.md muc 2).
 * Controller/service không tự bắt exception — để handler này xử lý.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorItem(fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Giá trị không hợp lệ"))
                .toList();
        String primaryMessage = !fieldErrors.isEmpty() ? fieldErrors.get(0).message() : ErrorCode.VALIDATION_ERROR.getDefaultMessage();
        return build(ErrorCode.VALIDATION_ERROR, request, fieldErrors, primaryMessage);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> new FieldErrorItem(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        String primaryMessage = !fieldErrors.isEmpty() ? fieldErrors.get(0).message() : ErrorCode.VALIDATION_ERROR.getDefaultMessage();
        return build(ErrorCode.VALIDATION_ERROR, request, fieldErrors, primaryMessage);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return build(ErrorCode.MALFORMED_JSON, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.MISSING_PARAMETER, request,
                List.of(new FieldErrorItem(ex.getParameterName(), "Tham số '" + ex.getParameterName() + "' là bắt buộc")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(ErrorCode.TYPE_MISMATCH, request,
                List.of(new FieldErrorItem(ex.getName(), "Giá trị '" + ex.getValue() + "' không đúng kiểu")));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        if (ex.getErrorCode().getStatus().is5xxServerError()) {
            log.error("Business exception (5xx): code={} message={}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Business exception: code={} message={}", ex.getErrorCode(), ex.getMessage());
        }
        ErrorResponse body = ErrorResponse.builder()
                .status(ex.getErrorCode().getStatus().value())
                .error(standardErrorName(ex.getErrorCode().getStatus()))
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .fieldErrors(List.of())
                .traceId(resolveTraceId())
                .build();
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: path={}", request.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: path={}", request.getRequestURI());
        return build(ErrorCode.UNAUTHORIZED, request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(
            DuplicateKeyException ex, HttpServletRequest request) {
        log.warn("Duplicate key: path={} message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.DUPLICATE, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: path={} message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.BAD_REQUEST, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: path={} method={}", request.getRequestURI(), request.getMethod(), ex);
        return build(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, HttpServletRequest request) {
        return build(errorCode, request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode errorCode, HttpServletRequest request, List<FieldErrorItem> fieldErrors) {
        return build(errorCode, request, fieldErrors, errorCode.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode errorCode, HttpServletRequest request, List<FieldErrorItem> fieldErrors, String customMessage) {
        HttpStatus status = errorCode.getStatus();
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(standardErrorName(status))
                .code(errorCode.name())
                .message(customMessage != null ? customMessage : errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .traceId(resolveTraceId())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private static String standardErrorName(HttpStatus status) {
        String reason = status.getReasonPhrase();
        return reason != null ? reason.toUpperCase().replace(' ', '_') : status.name();
    }

    private static String resolveTraceId() {
        String traceId = MDC.get(Constants.MDC_TRACE_ID);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
