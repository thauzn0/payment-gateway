package org.taha.paymentgateway.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.taha.paymentgateway.api.dto.request.AuthorizePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CapturePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CreatePaymentRequest;
import org.taha.paymentgateway.api.dto.request.RefundPaymentRequest;
import org.taha.paymentgateway.api.dto.response.PaymentAttemptResponse;
import org.taha.paymentgateway.api.dto.response.PaymentResponse;
import org.taha.paymentgateway.orchestrator.PaymentOrchestrator;
import org.taha.paymentgateway.persistence.entity.PaymentAttemptEntity;

import java.util.List;
import java.util.UUID;

/**
 * Payment API Controller
 * 
 * Tüm payment işlemleri bu controller üzerinden yapılır.
 * Merchant kimliği X-Merchant-Id header'ından alınır.
 */
@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrchestrator orchestrator;

    private static final String MERCHANT_HEADER = "X-Merchant-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    /**
     * POST /v1/payments
     * Yeni ödeme oluşturur (henüz authorize edilmemiş)
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(MERCHANT_HEADER) String merchantId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        log.info("Create payment request - merchantId: {}, amount: {} {}", 
                merchantId, request.amount(), request.currency());
        
        PaymentResponse response = orchestrator.createPayment(merchantId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /v1/payments/{paymentId}/authorize
     * Ödemeyi authorize eder (kartı bloke eder)
     */
    @PostMapping("/{paymentId}/authorize")
    public ResponseEntity<PaymentResponse> authorize(
            @PathVariable UUID paymentId,
            @RequestHeader(MERCHANT_HEADER) String merchantId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody AuthorizePaymentRequest request
    ) {
        log.info("Authorize payment request - paymentId: {}, merchantId: {}", paymentId, merchantId);
        
        PaymentResponse response = orchestrator.authorize(paymentId, merchantId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/payments/{paymentId}/capture
     * Authorized ödemeyi capture eder (parayı çeker)
     */
    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capture(
            @PathVariable UUID paymentId,
            @RequestHeader(MERCHANT_HEADER) String merchantId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) @Valid CapturePaymentRequest request
    ) {
        log.info("Capture payment request - paymentId: {}, merchantId: {}", paymentId, merchantId);
        
        PaymentResponse response = orchestrator.capture(paymentId, merchantId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/payments/{paymentId}/refund
     * Captured ödemeyi iade eder
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID paymentId,
            @RequestHeader(MERCHANT_HEADER) String merchantId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) @Valid RefundPaymentRequest request
    ) {
        log.info("Refund payment request - paymentId: {}, merchantId: {}", paymentId, merchantId);
        
        PaymentResponse response = orchestrator.refund(paymentId, merchantId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/payments/{paymentId}
     * Ödeme detayını getirir
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId,
            @RequestHeader(MERCHANT_HEADER) String merchantId
    ) {
        log.info("Get payment request - paymentId: {}, merchantId: {}", paymentId, merchantId);
        
        PaymentResponse response = orchestrator.getPayment(paymentId, merchantId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/payments/{paymentId}/attempts
     * Ödemenin provider attempt geçmişini getirir
     */
    @GetMapping("/{paymentId}/attempts")
    public ResponseEntity<List<PaymentAttemptResponse>> getAttempts(
            @PathVariable UUID paymentId,
            @RequestHeader(MERCHANT_HEADER) String merchantId
    ) {
        log.info("Get payment attempts request - paymentId: {}, merchantId: {}", paymentId, merchantId);
        
        List<PaymentAttemptEntity> attempts = orchestrator.getAttempts(paymentId, merchantId);
        List<PaymentAttemptResponse> response = attempts.stream()
                .map(this::toAttemptResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    private PaymentAttemptResponse toAttemptResponse(PaymentAttemptEntity entity) {
        return PaymentAttemptResponse.builder()
                .id(entity.getId())
                .paymentId(entity.getPaymentId())
                .provider(entity.getProvider())
                .operation(entity.getOperation())
                .status(entity.getStatus())
                .providerReference(entity.getProviderReference())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .latencyMs(entity.getLatencyMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
