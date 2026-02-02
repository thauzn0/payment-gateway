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
    private final ApiLogRepository apiLogRepository;
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

            // Komisyon hesapla
            BigDecimal commissionRate = card != null ? card.getCommissionRate() : new BigDecimal("1.99");
            BigDecimal commissionAmount = payment.getAmount()
                    .multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal netAmount = payment.getAmount().subtract(commissionAmount);

            // Authorize + Capture yap (demo için tek adımda)
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setProviderReference("DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCommissionRate(commissionRate);
            payment.setCommissionAmount(commissionAmount);
            payment.setNetAmount(netAmount);
            payment.setProviderName(card != null ? card.getBankName() : "MOCK_PROVIDER");
            paymentRepository.save(payment);

            // Attempt kaydet
            String bankName = card != null ? card.getBankName() : "Unknown";
            saveAttempt(payment.getId(), bankName, AttemptStatus.SUCCESS, null);

            log.info("Payment completed: {} - Commission: {}% = {} TL", paymentId, commissionRate, commissionAmount);

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
                        p.getCreatedAt(),
                        p.getCommissionRate(),
                        p.getCommissionAmount(),
                        p.getNetAmount(),
                        p.getProviderName()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Dashboard metrikleri.
     */
    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetrics> getMetrics() {
        List<PaymentEntity> allPayments = paymentRepository.findAll();
        List<ApiLogEntity> recentLogs = apiLogService.getRecentLogs();
        
        // Başarılı ödemeler
        List<PaymentEntity> captured = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                .toList();
        
        // Failed ödemeler
        long failedCount = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();
        
        // Success rate
        double successRate = allPayments.isEmpty() ? 0 : 
                (captured.size() * 100.0) / allPayments.size();
        
        // Toplam ciro
        BigDecimal totalRevenue = captured.stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Toplam komisyon
        BigDecimal totalCommission = captured.stream()
                .map(p -> p.getCommissionAmount() != null ? p.getCommissionAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Net gelir
        BigDecimal netRevenue = totalRevenue.subtract(totalCommission);
        
        // Provider dağılımı
        Map<String, Long> providerDistribution = captured.stream()
                .filter(p -> p.getProviderName() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        PaymentEntity::getProviderName,
                        java.util.stream.Collectors.counting()
                ));
        
        // API metrikleri
        long totalRequests = recentLogs.size();
        long successRequests = recentLogs.stream()
                .filter(l -> l.getResponseStatus() != null && l.getResponseStatus() >= 200 && l.getResponseStatus() < 300)
                .count();
        long errorRequests = recentLogs.stream()
                .filter(l -> l.getResponseStatus() != null && l.getResponseStatus() >= 400)
                .count();
        
        // Latency hesapla
        double avgLatency = recentLogs.stream()
                .filter(l -> l.getLatencyMs() != null)
                .mapToLong(ApiLogEntity::getLatencyMs)
                .average()
                .orElse(0);
        
        List<Long> latencies = recentLogs.stream()
                .filter(l -> l.getLatencyMs() != null)
                .map(ApiLogEntity::getLatencyMs)
                .sorted()
                .toList();
        
        long p50 = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);
        long p95 = latencies.isEmpty() ? 0 : latencies.get((int)(latencies.size() * 0.95));
        long p99 = latencies.isEmpty() ? 0 : latencies.get((int)(latencies.size() * 0.99));
        
        // Error rate
        double errorRate = totalRequests == 0 ? 0 : (errorRequests * 100.0) / totalRequests;
        
        return ResponseEntity.ok(new DashboardMetrics(
                allPayments.size(),
                captured.size(),
                failedCount,
                successRate,
                totalRevenue,
                totalCommission,
                netRevenue,
                providerDistribution,
                totalRequests,
                successRequests,
                errorRequests,
                errorRate,
                avgLatency,
                p50,
                p95,
                p99
        ));
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
     * API log istatistikleri.
     */
    @GetMapping("/api-logs/stats")
    public ResponseEntity<ApiLogStats> getApiLogStats() {
        long totalCount = apiLogRepository.count();
        long successCount = apiLogRepository.countByResponseStatusBetween(200, 299);
        long clientErrorCount = apiLogRepository.countByResponseStatusBetween(400, 499);
        long serverErrorCount = apiLogRepository.countByResponseStatusGreaterThanEqual(500);
        long errorCount = clientErrorCount + serverErrorCount;
        
        Double avgLatency = apiLogRepository.getAverageLatency();
        List<Long> latencies = apiLogRepository.getAllLatenciesSorted();
        
        long p50 = 0, p95 = 0, p99 = 0;
        if (!latencies.isEmpty()) {
            p50 = latencies.get(latencies.size() / 2);
            p95 = latencies.get(Math.min((int)(latencies.size() * 0.95), latencies.size() - 1));
            p99 = latencies.get(Math.min((int)(latencies.size() * 0.99), latencies.size() - 1));
        }
        
        double errorRate = totalCount == 0 ? 0 : (errorCount * 100.0) / totalCount;
        
        return ResponseEntity.ok(new ApiLogStats(
                totalCount,
                successCount,
                errorCount,
                errorRate,
                avgLatency != null ? avgLatency : 0,
                p50,
                p95,
                p99
        ));
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
    record PaymentListItem(UUID id, String orderId, BigDecimal amount, String currency, String status, String providerRef, String cardInfo, String description, OffsetDateTime createdAt, BigDecimal commissionRate, BigDecimal commissionAmount, BigDecimal netAmount, String providerName) {}
    record ApiLogDto(UUID id, String correlationId, UUID paymentId, String method, String endpoint, String requestBody, Integer responseStatus, String responseBody, Long latencyMs, OffsetDateTime createdAt) {}
    record PaymentDetailResponse(UUID id, String orderId, BigDecimal amount, String currency, String status, String providerRef, String cardBin, String cardLastFour, String description, OffsetDateTime createdAt, List<AttemptDto> attempts) {}
    record AttemptDto(UUID id, String provider, String operation, String status, String errorCode, String errorMessage, Long latencyMs, OffsetDateTime createdAt) {}
    record DashboardMetrics(
            int totalPayments,
            int capturedPayments,
            long failedPayments,
            double successRate,
            BigDecimal totalRevenue,
            BigDecimal totalCommission,
            BigDecimal netRevenue,
            Map<String, Long> providerDistribution,
            long totalRequests,
            long successRequests,
            long errorRequests,
            double errorRate,
            double avgLatency,
            long p50Latency,
            long p95Latency,
            long p99Latency
    ) {}
    record ApiLogStats(
            long totalRequests,
            long successRequests,
            long errorRequests,
            double errorRate,
            double avgLatency,
            long p50Latency,
            long p95Latency,
            long p99Latency
    ) {}
}
