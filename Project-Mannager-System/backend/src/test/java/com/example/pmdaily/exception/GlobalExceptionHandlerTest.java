package com.example.pmdaily.exception;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.example.pmdaily.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private JsonNode body(ResponseEntity<ErrorResponse> response) throws Exception {
        return objectMapper.valueToTree(response.getBody());
    }

    @Test
    void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "command");
        bindingResult.addError(new FieldError("command", "title", "Tiêu đề không được để trống"));
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(method, -1), bindingResult);

        ResponseEntity<ErrorResponse> response =
                handler.handleMethodArgumentNotValid(ex, request("/api/v1/tasks"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode json = body(response);
        assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/tasks");
        assertThat(json.get("fieldErrors").get(0).get("field").asText()).isEqualTo("title");
        assertThat(json.get("fieldErrors").get(0).get("message").asText())
                .isEqualTo("Tiêu đề không được để trống");
        assertThat(json.get("traceId").asText()).isNotBlank();
        assertThat(json.get("timestamp").asText()).isNotBlank();
    }

    @Test
    void businessException_mapsToErrorCode() throws Exception {
        BusinessException ex = new BusinessException(ErrorCode.NOT_PROJECT_MEMBER);
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request("/api/v1/tasks"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode json = body(response);
        assertThat(json.get("code").asText()).isEqualTo("NOT_PROJECT_MEMBER");
        assertThat(json.get("message").asText()).isEqualTo("Người thực hiện phải thuộc dự án");
    }

    @Test
    void resourceNotFound_returns404WithIdInMessage() throws Exception {
        ResourceNotFoundException ex = new ResourceNotFoundException("task", UUID.fromString(
                "00000000-0000-0000-0000-000000000401"));
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request("/api/v1/tasks/1"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode json = body(response);
        assertThat(json.get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.get("message").asText()).contains("Không tìm thấy task");
    }

    @Test
    void conflictException_returns409() {
        ConflictException ex = new ConflictException();
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request("/api/v1/tasks/1"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void accessDenied_returns403() throws Exception {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request("/api/v1/admin"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        JsonNode json = body(response);
        assertThat(json.get("code").asText()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void optimisticLocking_returns409() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Task", UUID.randomUUID());
        ResponseEntity<ErrorResponse> response =
                handler.handleOptimisticLocking(ex, request("/api/v1/tasks/1"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void genericException_returns500Hidden() throws Exception {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new IllegalStateException("secret detail"), request("/api/v1/x"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        JsonNode json = body(response);
        assertThat(json.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.get("message").asText()).doesNotContain("secret detail");
    }

    @Test
    void errorResponse_isValidJson_andHasAllFields() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("boom"), request("/api/v1/x"));
        JsonNode json = body(response);
        List<String> fields = new java.util.ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "code", "message", "path", "fieldErrors", "traceId");
    }

    @SuppressWarnings("unused")
    private void dummy() {
    }
}
