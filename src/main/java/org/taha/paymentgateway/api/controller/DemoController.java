package org.taha.paymentgateway.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.taha.paymentgateway.api.dto.request.CreatePaymentRequest;
import org.taha.paymentgateway.api.dto.response.PaymentResponse;
import org.taha.paymentgateway.core.exception.PaymentException;
import org.taha.paymentgateway.core.exception.PaymentNotFoundException;
import org.taha.paymentgateway.core.model.AttemptStatus;
import org.taha.paymentgateway.core.model.OperationType;
import org.taha.paymentgateway.core.model.PaymentStatus;
import org.taha.paymentgateway.observability.ApiLogService;
import org.taha.paymentgateway.orchestrator.PaymentOrchestrator;
import org.taha.paymentgateway.persistence.entity.*;
import org.taha.paymentgateway.persistence.repository.*;
import org.taha.paymentgateway.threeds.ThreeDsService;
import org.taha.paymentgateway.threeds.ThreeDsService.ThreeDsVerifyResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Demo frontend için API endpoint'leri.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemoController {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final TestCardRepository testCardRepository;
    private final ThreeDsService threeDsService;
    private final ApiLogService apiLogService;

    /**
     * Test kartlarını listele.
     */
    @GetMapping("/test-cards")
    public ResponseEntity<List<TestCardDto>> getTestCards() {
        List<TestCardEntity> cards = testCardRepository.findAll();
        List<TestCardDto> result = cards.stream()
                .map(c -> new TestCardDto(
                        maskCardNumber(c.getCardNumber()),
                        c.getCardNumber(), // Demo için göster
                        c.getCardHolder(),
                        c.getExpiryMonth(),
                        c.getExpiryYear(),
                        c.getCvv(),
                        c.getBankName(),
                        c.getCardBrand(),
                        c.getCommissionRate(),
                        c.isShouldFail()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Sipariş oluştur ve ödeme başlat.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("Creating demo order: {}", request);

        // Payment oluştur
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .merchantId("DEMO_MERCHANT")
                .amount(request.amount())
                .currency("TRY")
                .orderId("ORD-" + System.currentTimeMillis())
                .customerEmail(request.email())
                .description(request.productName())
                .status(PaymentStatus.CREATED)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Demo order created: {}", payment.getId());

        return ResponseEntity.ok(new OrderResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                "CREATED"
        ));
    }

    /**
     * Kart ile ödeme yap (3DS öncesi).
     */
    @PostMapping("/payments/{paymentId}/pay")
    public ResponseEntity<PaymentResult> processPayment(
            @PathVariable UUID paymentId,
            @RequestBody CardPaymentRequest request
    ) {
        log.info("Processing payment: {} with card: {}****", paymentId, request.cardNumber().substring(0, 6));

        // Payment'ı bul
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new PaymentException("INVALID_STATE", "Payment already processed");
        }

        // Kartı doğrula
        String cleanCardNumber = request.cardNumber().replaceAll("\\s", "");
        TestCardEntity card = testCardRepository.findByCardNumber(cleanCardNumber)
                .orElseThrow(() -> new PaymentException("INVALID_CARD", "Kart bulunamadı. Test kartlarından birini kullanın."));

        // CVV ve tarih kontrolü
        if (!card.getCvv().equals(request.cvv())) {
            throw new PaymentException("INVALID_CVV", "CVV hatalı");
        }

        // Kart bilgilerini kaydet
        payment.setCardBin(cleanCardNumber.substring(0, 6));
        payment.setCardLastFour(cleanCardNumber.substring(cleanCardNumber.length() - 4));

        // Başarısız olması gereken kart mı?
        if (card.isShouldFail()) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // Attempt kaydet
            saveAttempt(payment.getId(), card.getBankName(), AttemptStatus.FAILURE, card.getFailReason());

            return ResponseEntity.ok(new PaymentResult(
                    false,
                    null,
                    card.getFailReason(),
                    "FAILED",
                    null
            ));
        }

        // 3DS session oluştur
        ThreeDsSessionEntity session = threeDsService.createSession(paymentId);
        paymentRepository.save(payment);

        log.info("3DS required for payment: {}", paymentId);

        return ResponseEntity.ok(new PaymentResult(
                true,
                session.getId(),
                "3D Secure doğrulaması gerekli",
                "REQUIRES_3DS",
                card.getBankName()
        ));
    }

    /**
     * 3DS OTP doğrula.
     */
    @PostMapping("/payments/{paymentId}/verify-3ds")
    public ResponseEntity<ThreeDsResult> verify3ds(
            @PathVariable UUID paymentId,
            @RequestBody Verify3dsRequest request
    ) {
        log.info("Verifying 3DS for payment: {}", paymentId);

        ThreeDsVerifyResult result = threeDsService.verify(paymentId, request.otp());

        if (result.success()) {
            // Payment'ı bul ve güncelle
            PaymentEntity payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            // Kartı bul
            TestCardEntity card = testCardRepository.findByBinPrefix(payment.getCardBin())
                    .orElse(null);

            // Authorize + Capture yap (demo için tek adımda)
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setProviderReference("DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            paymentRepository.save(payment);

            // Attempt kaydet
            String bankName = card != null ? card.getBankName() : "Unknown";
            saveAttempt(payment.getId(), bankName, AttemptStatus.SUCCESS, null);

            log.info("Payment completed: {}", paymentId);

            return ResponseEntity.ok(new ThreeDsResult(
                    true,
                    result.message(),
                    "SUCCESS",
                    payment.getProviderReference()
            ));
        }

        return ResponseEntity.ok(new ThreeDsResult(
                false,
                result.message(),
                result.code(),
                null
        ));
    }

    /**
     * Tüm ödemeleri listele (Admin Dashboard için).
     */
    @GetMapping("/payments")
    public ResponseEntity<List<PaymentListItem>> listPayments() {
        List<PaymentEntity> payments = paymentRepository.findAll();
        
        // Son ödemeler önce gelsin
        payments.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        List<PaymentListItem> result = payments.stream()
                .map(p -> new PaymentListItem(
                        p.getId(),
                        p.getOrderId(),
                        p.getAmount(),
                        p.getCurrency(),
                        p.getStatus().name(),
                        p.getProviderReference(),
                        p.getCardBin() != null ? p.getCardBin() + "****" + p.getCardLastFour() : null,
                        p.getDescription(),
                        p.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * API loglarını getir.
     */
    @GetMapping("/api-logs")
    public ResponseEntity<List<ApiLogDto>> getApiLogs() {
        List<ApiLogEntity> logs = apiLogService.getRecentLogs();

        List<ApiLogDto> result = logs.stream()
                .map(l -> new ApiLogDto(
                        l.getId(),
                        l.getCorrelationId(),
                        l.getPaymentId(),
                        l.getHttpMethod(),
                        l.getEndpoint(),
                        l.getRequestBody(),
                        l.getResponseStatus(),
                        l.getResponseBody(),
                        l.getLatencyMs(),
                        l.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Payment detayı.
     */
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getPaymentDetail(@PathVariable UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        List<PaymentAttemptEntity> attempts = attemptRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);

        return ResponseEntity.ok(new PaymentDetailResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getProviderReference(),
                payment.getCardBin(),
                payment.getCardLastFour(),
                payment.getDescription(),
                payment.getCreatedAt(),
                attempts.stream().map(a -> new AttemptDto(
                        a.getId(),
                        a.getProvider(),
                        a.getOperation().name(),
                        a.getStatus().name(),
                        a.getErrorCode(),
                        a.getErrorMessage(),
                        a.getLatencyMs(),
                        a.getCreatedAt()
                )).toList()
        ));
    }

    // Helper method
    private void saveAttempt(UUID paymentId, String provider, AttemptStatus status, String errorCode) {
        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .provider(provider)
                .operation(OperationType.AUTHORIZE)
                .status(status)
                .errorCode(errorCode)
                .latencyMs(100 + new Random().nextInt(200))
                .build();
        attemptRepository.save(attempt);
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 10) return cardNumber;
        return cardNumber.substring(0, 6) + "******" + cardNumber.substring(cardNumber.length() - 4);
    }

    // DTOs
    record CreateOrderRequest(String productName, BigDecimal amount, String email) {}
    record OrderResponse(UUID paymentId, String orderId, BigDecimal amount, String status) {}
    record CardPaymentRequest(String cardNumber, String cardHolder, String expiryMonth, String expiryYear, String cvv) {}
    record PaymentResult(boolean proceed, UUID threeDsSessionId, String message, String status, String bankName) {}
    record Verify3dsRequest(String otp) {}
    record ThreeDsResult(boolean success, String message, String code, String providerReference) {}
    record TestCardDto(String maskedNumber, String fullNumber, String holder, String expiryMonth, String expiryYear, String cvv, String bankName, String brand, BigDecimal commission, boolean willFail) {}
    record PaymentListItem(UUID id, String orderId, BigDecimal amount, String currency, String status, String providerRef, String cardInfo, String description, OffsetDateTime createdAt) {}
    record ApiLogDto(UUID id, String correlationId, UUID paymentId, String method, String endpoint, String requestBody, Integer responseStatus, String responseBody, Long latencyMs, OffsetDateTime createdAt) {}
    record PaymentDetailResponse(UUID id, String orderId, BigDecimal amount, String currency, String status, String providerRef, String cardBin, String cardLastFour, String description, OffsetDateTime createdAt, List<AttemptDto> attempts) {}
    record AttemptDto(UUID id, String provider, String operation, String status, String errorCode, String errorMessage, Long latencyMs, OffsetDateTime createdAt) {}
}
