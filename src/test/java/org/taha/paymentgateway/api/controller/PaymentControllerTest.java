package org.taha.paymentgateway.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.taha.paymentgateway.api.dto.request.AuthorizePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CapturePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CreatePaymentRequest;
import org.taha.paymentgateway.api.dto.request.RefundPaymentRequest;
import org.taha.paymentgateway.api.dto.response.PaymentResponse;
import org.taha.paymentgateway.core.exception.PaymentNotFoundException;
import org.taha.paymentgateway.core.model.PaymentStatus;
import org.taha.paymentgateway.orchestrator.PaymentOrchestrator;
import org.taha.paymentgateway.persistence.entity.PaymentAttemptEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentOrchestrator orchestrator;

    private static final String MERCHANT_HEADER = "X-Merchant-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String BASE_URL = "/v1/payments";

    private UUID paymentId;
    private String merchantId;
    private PaymentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        merchantId = "merchant-test-123";
        sampleResponse = PaymentResponse.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("100.00"))
                .currency("TRY")
                .orderId("order-123")
                .customerEmail("test@example.com")
                .status(PaymentStatus.CREATED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /v1/payments - Create Payment")
    class CreatePaymentTests {

        @Test
        @DisplayName("should create payment successfully")
        void shouldCreatePayment_Successfully() throws Exception {
            // given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .orderId("order-123")
                    .customerEmail("test@example.com")
                    .build();

            when(orchestrator.createPayment(eq(merchantId), any(CreatePaymentRequest.class), isNull()))
                    .thenReturn(sampleResponse);

            // when/then
            mockMvc.perform(post(BASE_URL)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(paymentId.toString()))
                    .andExpect(jsonPath("$.merchantId").value(merchantId))
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.currency").value("TRY"))
                    .andExpect(jsonPath("$.status").value("CREATED"));

            verify(orchestrator).createPayment(eq(merchantId), any(CreatePaymentRequest.class), isNull());
        }

        @Test
        @DisplayName("should create payment with idempotency key")
        void shouldCreatePayment_WithIdempotencyKey() throws Exception {
            // given
            String idempotencyKey = "unique-key-123";
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            when(orchestrator.createPayment(eq(merchantId), any(CreatePaymentRequest.class), eq(idempotencyKey)))
                    .thenReturn(sampleResponse);

            // when/then
            mockMvc.perform(post(BASE_URL)
                            .header(MERCHANT_HEADER, merchantId)
                            .header(IDEMPOTENCY_HEADER, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(orchestrator).createPayment(eq(merchantId), any(CreatePaymentRequest.class), eq(idempotencyKey));
        }

        @Test
        @DisplayName("should return 400 when amount is missing")
        void shouldReturn400_WhenAmountMissing() throws Exception {
            // given
            String requestJson = "{\"currency\":\"TRY\"}";

            // when/then
            mockMvc.perform(post(BASE_URL)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when currency is invalid")
        void shouldReturn400_WhenCurrencyInvalid() throws Exception {
            // given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("INVALID")
                    .build();

            // when/then
            mockMvc.perform(post(BASE_URL)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when merchant header is missing")
        void shouldReturn400_WhenMerchantHeaderMissing() throws Exception {
            // given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            // when/then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{id}/authorize - Authorize Payment")
    class AuthorizePaymentTests {

        @Test
        @DisplayName("should authorize payment successfully")
        void shouldAuthorizePayment_Successfully() throws Exception {
            // given
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            PaymentResponse authorizedResponse = PaymentResponse.builder()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(PaymentStatus.AUTHORIZED)
                    .providerReference("AUTH-REF-123")
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(orchestrator.authorize(eq(paymentId), eq(merchantId), any(AuthorizePaymentRequest.class), isNull()))
                    .thenReturn(authorizedResponse);

            // when/then
            mockMvc.perform(post(BASE_URL + "/{paymentId}/authorize", paymentId)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.providerReference").value("AUTH-REF-123"));
        }

        @Test
        @DisplayName("should return 404 when payment not found")
        void shouldReturn404_WhenPaymentNotFound() throws Exception {
            // given
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            when(orchestrator.authorize(eq(paymentId), eq(merchantId), any(AuthorizePaymentRequest.class), isNull()))
                    .thenThrow(new PaymentNotFoundException("Payment not found: " + paymentId));

            // when/then
            mockMvc.perform(post(BASE_URL + "/{paymentId}/authorize", paymentId)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{id}/capture - Capture Payment")
    class CapturePaymentTests {

        @Test
        @DisplayName("should capture payment successfully")
        void shouldCapturePayment_Successfully() throws Exception {
            // given
            CapturePaymentRequest request = CapturePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            PaymentResponse capturedResponse = PaymentResponse.builder()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(PaymentStatus.CAPTURED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(orchestrator.capture(eq(paymentId), eq(merchantId), any(), isNull()))
                    .thenReturn(capturedResponse);

            // when/then
            mockMvc.perform(post(BASE_URL + "/{paymentId}/capture", paymentId)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CAPTURED"));
        }

        @Test
        @DisplayName("should capture payment without body")
        void shouldCapturePayment_WithoutBody() throws Exception {
            // given
            PaymentResponse capturedResponse = PaymentResponse.builder()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .status(PaymentStatus.CAPTURED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(orchestrator.capture(eq(paymentId), eq(merchantId), isNull(), isNull()))
                    .thenReturn(capturedResponse);

            // when/then
            mockMvc.perform(post(BASE_URL + "/{paymentId}/capture", paymentId)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{id}/refund - Refund Payment")
    class RefundPaymentTests {

        @Test
        @DisplayName("should refund payment successfully")
        void shouldRefundPayment_Successfully() throws Exception {
            // given
            RefundPaymentRequest request = RefundPaymentRequest.builder()
                    .amount(new BigDecimal("50.00"))
                    .reason("Customer request")
                    .build();

            PaymentResponse refundedResponse = PaymentResponse.builder()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .status(PaymentStatus.PARTIALLY_REFUNDED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(orchestrator.refund(eq(paymentId), eq(merchantId), any(), isNull()))
                    .thenReturn(refundedResponse);

            // when/then
            mockMvc.perform(post(BASE_URL + "/{paymentId}/refund", paymentId)
                            .header(MERCHANT_HEADER, merchantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        }
    }

    @Nested
    @DisplayName("GET /v1/payments/{id} - Get Payment")
    class GetPaymentTests {

        @Test
        @DisplayName("should get payment successfully")
        void shouldGetPayment_Successfully() throws Exception {
            // given
            when(orchestrator.getPayment(paymentId, merchantId)).thenReturn(sampleResponse);

            // when/then
            mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                            .header(MERCHANT_HEADER, merchantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(paymentId.toString()))
                    .andExpect(jsonPath("$.merchantId").value(merchantId));
        }

        @Test
        @DisplayName("should return 404 when payment not found")
        void shouldReturn404_WhenPaymentNotFound() throws Exception {
            // given
            when(orchestrator.getPayment(paymentId, merchantId))
                    .thenThrow(new PaymentNotFoundException("Payment not found"));

            // when/then
            mockMvc.perform(get(BASE_URL + "/{paymentId}", paymentId)
                            .header(MERCHANT_HEADER, merchantId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for invalid UUID")
        void shouldReturn400_ForInvalidUUID() throws Exception {
            // when/then
            mockMvc.perform(get(BASE_URL + "/{paymentId}", "invalid-uuid")
                            .header(MERCHANT_HEADER, merchantId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/payments/{id}/attempts - Get Payment Attempts")
    class GetAttemptsTests {

        @Test
        @DisplayName("should get payment attempts successfully")
        void shouldGetAttempts_Successfully() throws Exception {
            // given
            PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(paymentId)
                    .provider("MOCK_PROVIDER")
                    .operation(org.taha.paymentgateway.core.model.OperationType.AUTHORIZE)
                    .status(org.taha.paymentgateway.core.model.AttemptStatus.SUCCESS)
                    .providerReference("REF-123")
                    .latencyMs(150L)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(orchestrator.getAttempts(paymentId, merchantId)).thenReturn(List.of(attempt));

            // when/then
            mockMvc.perform(get(BASE_URL + "/{paymentId}/attempts", paymentId)
                            .header(MERCHANT_HEADER, merchantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].provider").value("MOCK_PROVIDER"))
                    .andExpect(jsonPath("$[0].status").value("SUCCESS"));
        }

        @Test
        @DisplayName("should return empty list when no attempts")
        void shouldReturnEmptyList_WhenNoAttempts() throws Exception {
            // given
            when(orchestrator.getAttempts(paymentId, merchantId)).thenReturn(List.of());

            // when/then
            mockMvc.perform(get(BASE_URL + "/{paymentId}/attempts", paymentId)
                            .header(MERCHANT_HEADER, merchantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }
}
