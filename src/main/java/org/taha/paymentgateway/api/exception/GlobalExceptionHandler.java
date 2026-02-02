package org.taha.paymentgateway.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.taha.paymentgateway.api.dto.response.ApiErrorResponse;
import org.taha.paymentgateway.core.exception.*;
import org.taha.paymentgateway.observability.CorrelationIdFilter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Global exception handler for REST API.
 * Tüm hataları standart formata dönüştürür.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Payment bulunamadı - 404
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        log.warn("Payment not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Geçersiz payment durumu - 409 Conflict
     */
    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidPaymentStateException ex) {
        log.warn("Invalid payment state: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Idempotency conflict - 409 Conflict
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Provider hatası - 502 Bad Gateway
     */
    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderError(ProviderException ex) {
        log.error("Provider error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Genel payment exception - 400
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentException(PaymentException ex) {
        log.warn("Payment error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Validation hatası - 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        ApiErrorResponse response = ApiErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(fieldErrors)
                .traceId(generateTraceId())
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Eksik header - 400
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing header: {}", ex.getHeaderName());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("MISSING_HEADER", "Required header missing: " + ex.getHeaderName()));
    }

    /**
     * Type mismatch (örn: geçersiz UUID) - 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {} - {}", ex.getName(), ex.getValue());
        
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("INVALID_PARAMETER", message));
    }

    /**
     * Beklenmeyen hatalar - 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericError(Exception ex) {
        log.error("Unexpected error", ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ApiErrorResponse buildErrorResponse(String errorCode, String message) {
        return ApiErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .traceId(generateTraceId())
                .timestamp(OffsetDateTime.now())
                .build();
    }

    private ApiErrorResponse.FieldError toFieldError(FieldError fe) {
        return ApiErrorResponse.FieldError.builder()
                .field(fe.getField())
                .message(fe.getDefaultMessage())
                .rejectedValue(fe.getRejectedValue())
                .build();
    }

    private String generateTraceId() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString().substring(0, 8);
    }
}
