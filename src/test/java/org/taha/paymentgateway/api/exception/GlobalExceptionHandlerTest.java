package org.taha.paymentgateway.api.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.taha.paymentgateway.api.dto.response.ApiErrorResponse;
import org.taha.paymentgateway.core.exception.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("PaymentNotFoundException handling")
    class PaymentNotFoundTests {

        @Test
        @DisplayName("should return 404 NOT_FOUND for PaymentNotFoundException")
        void shouldReturn404_ForPaymentNotFoundException() {
            // given
            PaymentNotFoundException ex = new PaymentNotFoundException("Payment not found: 123");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handlePaymentNotFound(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("PAYMENT_NOT_FOUND");
            assertThat(response.getBody().message()).contains("Payment not found");
            assertThat(response.getBody().traceId()).isNotNull();
            assertThat(response.getBody().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("InvalidPaymentStateException handling")
    class InvalidPaymentStateTests {

        @Test
        @DisplayName("should return 409 CONFLICT for InvalidPaymentStateException")
        void shouldReturn409_ForInvalidPaymentStateException() {
            // given
            InvalidPaymentStateException ex = new InvalidPaymentStateException("Cannot capture: payment not authorized");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleInvalidState(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_PAYMENT_STATE");
            assertThat(response.getBody().message()).contains("Cannot capture");
        }
    }

    @Nested
    @DisplayName("IdempotencyConflictException handling")
    class IdempotencyConflictTests {

        @Test
        @DisplayName("should return 409 CONFLICT for IdempotencyConflictException")
        void shouldReturn409_ForIdempotencyConflictException() {
            // given
            IdempotencyConflictException ex = new IdempotencyConflictException("Different request with same idempotency key");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIdempotencyConflict(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
    }

    @Nested
    @DisplayName("ProviderException handling")
    class ProviderExceptionTests {

        @Test
        @DisplayName("should return 502 BAD_GATEWAY for ProviderException")
        void shouldReturn502_ForProviderException() {
            // given
            ProviderException ex = new ProviderException("Provider timeout");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleProviderError(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("PROVIDER_ERROR");
            assertThat(response.getBody().message()).contains("timeout");
        }
    }

    @Nested
    @DisplayName("PaymentException handling")
    class PaymentExceptionTests {

        @Test
        @DisplayName("should return 400 BAD_REQUEST for generic PaymentException")
        void shouldReturn400_ForPaymentException() {
            // given
            PaymentException ex = new PaymentException("PAYMENT_ERROR", "Invalid payment data");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handlePaymentException(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("PAYMENT_ERROR");
        }
    }

    @Nested
    @DisplayName("Validation exception handling")
    class ValidationExceptionTests {

        @Test
        @DisplayName("should return 400 with field errors for validation exception")
        void shouldReturn400_WithFieldErrors_ForValidationException() {
            // given
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError1 = new FieldError("request", "amount", null, false, null, null, "Amount is required");
            FieldError fieldError2 = new FieldError("request", "currency", "XX", false, null, null, "Currency must be 3 characters");
            
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
            
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidation(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Request validation failed");
            assertThat(response.getBody().details()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Missing header handling")
    class MissingHeaderTests {

        @Test
        @DisplayName("should return 400 for missing required header")
        void shouldReturn400_ForMissingHeader() throws Exception {
            // given
            MissingRequestHeaderException ex = new MissingRequestHeaderException("X-Merchant-Id", null);

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMissingHeader(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("MISSING_HEADER");
            assertThat(response.getBody().message()).contains("X-Merchant-Id");
        }
    }

    @Nested
    @DisplayName("Type mismatch handling")
    class TypeMismatchTests {

        @Test
        @DisplayName("should return 400 for type mismatch")
        void shouldReturn400_ForTypeMismatch() {
            // given
            MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
            when(ex.getName()).thenReturn("paymentId");
            when(ex.getValue()).thenReturn("not-a-uuid");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleTypeMismatch(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("INVALID_PARAMETER");
            assertThat(response.getBody().message()).contains("paymentId");
            assertThat(response.getBody().message()).contains("not-a-uuid");
        }
    }

    @Nested
    @DisplayName("Generic exception handling")
    class GenericExceptionTests {

        @Test
        @DisplayName("should return 500 INTERNAL_SERVER_ERROR for unexpected exception")
        void shouldReturn500_ForUnexpectedException() {
            // given
            Exception ex = new RuntimeException("Unexpected error");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericError(ex);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        }

        @Test
        @DisplayName("should not expose internal error details")
        void shouldNotExposeInternalErrorDetails() {
            // given
            Exception ex = new NullPointerException("Sensitive stack trace info");

            // when
            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericError(ex);

            // then
            assertThat(response.getBody().message()).doesNotContain("Sensitive");
            assertThat(response.getBody().message()).doesNotContain("NullPointer");
        }
    }
}
