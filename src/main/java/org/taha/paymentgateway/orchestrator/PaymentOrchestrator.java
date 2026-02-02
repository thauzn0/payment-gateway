package org.taha.paymentgateway.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.taha.paymentgateway.api.dto.request.AuthorizePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CapturePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CreatePaymentRequest;
import org.taha.paymentgateway.api.dto.request.RefundPaymentRequest;
import org.taha.paymentgateway.api.dto.response.PaymentResponse;
import org.taha.paymentgateway.core.exception.IdempotencyConflictException;
import org.taha.paymentgateway.core.exception.InvalidPaymentStateException;
import org.taha.paymentgateway.core.exception.PaymentNotFoundException;
import org.taha.paymentgateway.core.exception.ProviderException;
import org.taha.paymentgateway.core.model.*;
import org.taha.paymentgateway.idempotency.IdempotencyService;
import org.taha.paymentgateway.persistence.entity.*;
import org.taha.paymentgateway.persistence.repository.*;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter.*;
import org.taha.paymentgateway.routing.RoutingEngine;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingContext;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ödeme akışını yöneten ana servis.
 * 
 * Sorumlulukları:
 * - Idempotency kontrolü
 * - Payment state kontrolü
 * - Provider adapter çağrısı
 * - Sonuçların DB'ye yazılması
 * - Event üretimi (outbox)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final RoutingEngine routingEngine;
    private final Map<String, PaymentProviderAdapter> providerAdapters;
    private final ObjectMapper objectMapper;

    // ========== CREATE PAYMENT ==========

    @Transactional
    public PaymentResponse createPayment(String merchantId, CreatePaymentRequest request, String idempotencyKey) {
        log.info("Creating payment - merchantId: {}, amount: {} {}, idempotencyKey: {}", 
                merchantId, request.amount(), request.currency(), idempotencyKey);

        // Idempotency kontrolü
        if (idempotencyKey != null) {
            Optional<String> cachedResponse = checkIdempotency(idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                log.info("Returning cached response for idempotencyKey: {}", idempotencyKey);
                return deserialize(cachedResponse.get(), PaymentResponse.class);
            }
        }

        // Payment oluştur
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .merchantId(merchantId)
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .orderId(request.orderId())
                .customerEmail(maskEmail(request.customerEmail()))
                .description(request.description())
                .status(PaymentStatus.CREATED)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created - id: {}, status: {}", payment.getId(), payment.getStatus());

        // Outbox event
        publishEvent(payment, "PaymentCreated");

        PaymentResponse response = toResponse(payment);

        // Idempotency kaydet
        if (idempotencyKey != null) {
            saveIdempotency(idempotencyKey, request, response);
        }

        return response;
    }

    // ========== AUTHORIZE ==========

    @Transactional
    public PaymentResponse authorize(UUID paymentId, String merchantId, AuthorizePaymentRequest request, String idempotencyKey) {
        log.info("Authorizing payment - paymentId: {}, idempotencyKey: {}", paymentId, idempotencyKey);

        // Idempotency kontrolü
        if (idempotencyKey != null) {
            Optional<String> cachedResponse = checkIdempotency(idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                log.info("Returning cached response for idempotencyKey: {}", idempotencyKey);
                return deserialize(cachedResponse.get(), PaymentResponse.class);
            }
        }

        // Payment bul ve kontrol et
        PaymentEntity payment = getPaymentForMerchant(paymentId, merchantId);
        
        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new InvalidPaymentStateException(paymentId, payment.getStatus(), "CREATED");
        }

        // Routing: Kart BIN'ine göre en uygun provider'ı seç
        RoutingResult routing = routingEngine.selectProvider(
                RoutingContext.of(merchantId, payment.getCurrency(), request.cardBin())
        );
        PaymentProviderAdapter selectedProvider = routing.provider();
        log.info("Routing sonucu: {} (komisyon: {}%, sebep: {})", 
                routing.providerName(), routing.commissionRate(), routing.reason());

        // Provider'a authorize isteği
        long startTime = System.currentTimeMillis();
        ProviderResult result = selectedProvider.authorize(new AuthorizeContext(
                paymentId,
                payment.getAmount(),
                payment.getCurrency(),
                request.cardToken(),
                request.cardBin(),
                merchantId
        ));
        long latencyMs = System.currentTimeMillis() - startTime;

        // Attempt kaydet (hangi provider kullanıldığı bilgisiyle)
        saveAttempt(payment.getId(), OperationType.AUTHORIZE, result, latencyMs, routing.providerName());

        // Sonuca göre güncelle
        if (result.status() == AttemptStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setProviderReference(result.providerReference());
            publishEvent(payment, "PaymentAuthorized");
            log.info("Payment authorized successfully - paymentId: {}, provider: {}", paymentId, routing.providerName());
        } else if (result.status() == AttemptStatus.REQUIRES_3DS) {
            // 3DS gerekli - payment henüz CREATED kalır
            log.info("Payment requires 3DS - paymentId: {}, url: {}", paymentId, result.threeDSUrl());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            publishEvent(payment, "PaymentFailed");
            log.warn("Payment authorization failed - paymentId: {}, error: {}", paymentId, result.errorMessage());
            throw new ProviderException(routing.providerName(), result.errorCode(), result.errorMessage());
        }

        payment = paymentRepository.save(payment);
        PaymentResponse response = toResponse(payment);

        // Idempotency kaydet
        if (idempotencyKey != null) {
            saveIdempotency(idempotencyKey, request, response);
        }

        return response;
    }

    // ========== CAPTURE ==========

    @Transactional
    public PaymentResponse capture(UUID paymentId, String merchantId, CapturePaymentRequest request, String idempotencyKey) {
        log.info("Capturing payment - paymentId: {}, amount: {}", paymentId, request != null ? request.amount() : "full");

        // Idempotency kontrolü
        if (idempotencyKey != null) {
            Optional<String> cachedResponse = checkIdempotency(idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                log.info("Returning cached response for idempotencyKey: {}", idempotencyKey);
                return deserialize(cachedResponse.get(), PaymentResponse.class);
            }
        }

        // Payment bul ve kontrol et
        PaymentEntity payment = getPaymentForMerchant(paymentId, merchantId);
        
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException(paymentId, payment.getStatus(), "AUTHORIZED");
        }

        BigDecimal captureAmount = (request != null && request.amount() != null) 
                ? request.amount() 
                : payment.getAmount();

        // Authorize'da kullanılan provider'ı bul
        String providerName = getLastAuthorizeProvider(paymentId);
        PaymentProviderAdapter provider = providerAdapters.get(providerName);
        if (provider == null) {
            provider = providerAdapters.values().iterator().next(); // fallback
            providerName = provider.providerName();
        }
        log.info("Capture için provider: {}", providerName);

        // Provider'a capture isteği
        long startTime = System.currentTimeMillis();
        ProviderResult result = provider.capture(new CaptureContext(
                paymentId,
                payment.getProviderReference(),
                captureAmount,
                payment.getCurrency()
        ));
        long latencyMs = System.currentTimeMillis() - startTime;

        // Attempt kaydet
        saveAttempt(payment.getId(), OperationType.CAPTURE, result, latencyMs, providerName);

        // Sonuca göre güncelle
        if (result.status() == AttemptStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.CAPTURED);
            
            // Transaction kaydet
            saveTransaction(payment.getId(), OperationType.CAPTURE, captureAmount, result.providerReference());
            
            publishEvent(payment, "PaymentCaptured");
            log.info("Payment captured successfully - paymentId: {}, amount: {}", paymentId, captureAmount);
        } else {
            log.warn("Payment capture failed - paymentId: {}, error: {}", paymentId, result.errorMessage());
            throw new ProviderException(providerName, result.errorCode(), result.errorMessage());
        }

        payment = paymentRepository.save(payment);
        PaymentResponse response = toResponse(payment);

        // Idempotency kaydet
        if (idempotencyKey != null) {
            saveIdempotency(idempotencyKey, request, response);
        }

        return response;
    }

    // ========== REFUND ==========

    @Transactional
    public PaymentResponse refund(UUID paymentId, String merchantId, RefundPaymentRequest request, String idempotencyKey) {
        log.info("Refunding payment - paymentId: {}, amount: {}", paymentId, request != null ? request.amount() : "full");

        // Idempotency kontrolü
        if (idempotencyKey != null) {
            Optional<String> cachedResponse = checkIdempotency(idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                log.info("Returning cached response for idempotencyKey: {}", idempotencyKey);
                return deserialize(cachedResponse.get(), PaymentResponse.class);
            }
        }

        // Payment bul ve kontrol et
        PaymentEntity payment = getPaymentForMerchant(paymentId, merchantId);
        
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new InvalidPaymentStateException(paymentId, payment.getStatus(), "CAPTURED or PARTIALLY_REFUNDED");
        }

        BigDecimal refundAmount = (request != null && request.amount() != null) 
                ? request.amount() 
                : payment.getAmount();
        
        String reason = (request != null) ? request.reason() : null;

        // Authorize'da kullanılan provider'ı bul
        String providerName = getLastAuthorizeProvider(paymentId);
        PaymentProviderAdapter provider = providerAdapters.get(providerName);
        if (provider == null) {
            provider = providerAdapters.values().iterator().next(); // fallback
            providerName = provider.providerName();
        }
        log.info("Refund için provider: {}", providerName);

        // Provider'a refund isteği
        long startTime = System.currentTimeMillis();
        ProviderResult result = provider.refund(new RefundContext(
                paymentId,
                payment.getProviderReference(),
                refundAmount,
                payment.getCurrency(),
                reason
        ));
        long latencyMs = System.currentTimeMillis() - startTime;

        // Attempt kaydet
        saveAttempt(payment.getId(), OperationType.REFUND, result, latencyMs, providerName);

        // Sonuca göre güncelle
        if (result.status() == AttemptStatus.SUCCESS) {
            // Tam iade mi kısmi iade mi?
            BigDecimal totalRefunded = calculateTotalRefunded(payment.getId()).add(refundAmount);
            
            if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
            
            // Transaction kaydet
            saveTransaction(payment.getId(), OperationType.REFUND, refundAmount, result.providerReference());
            
            publishEvent(payment, "PaymentRefunded");
            log.info("Payment refunded successfully - paymentId: {}, amount: {}", paymentId, refundAmount);
        } else {
            log.warn("Payment refund failed - paymentId: {}, error: {}", paymentId, result.errorMessage());
            throw new ProviderException(providerName, result.errorCode(), result.errorMessage());
        }

        payment = paymentRepository.save(payment);
        PaymentResponse response = toResponse(payment);

        // Idempotency kaydet
        if (idempotencyKey != null) {
            saveIdempotency(idempotencyKey, request, response);
        }

        return response;
    }

    // ========== QUERY ==========

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, String merchantId) {
        return toResponse(getPaymentForMerchant(paymentId, merchantId));
    }

    @Transactional(readOnly = true)
    public List<PaymentAttemptEntity> getAttempts(UUID paymentId, String merchantId) {
        // Önce payment'ın merchant'a ait olduğunu doğrula
        getPaymentForMerchant(paymentId, merchantId);
        return attemptRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }

    // ========== HELPER METHODS ==========

    /**
     * Authorize işleminde kullanılan provider'ı bul.
     * Capture ve Refund için aynı provider kullanılmalı.
     */
    private String getLastAuthorizeProvider(UUID paymentId) {
        return attemptRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .filter(a -> a.getOperation() == OperationType.AUTHORIZE && a.getStatus() == AttemptStatus.SUCCESS)
                .findFirst()
                .map(PaymentAttemptEntity::getProvider)
                .orElse("MOCK_PROVIDER");
    }

    private PaymentEntity getPaymentForMerchant(UUID paymentId, String merchantId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        
        if (!payment.getMerchantId().equals(merchantId)) {
            throw new PaymentNotFoundException(paymentId);
        }
        
        return payment;
    }

    private Optional<String> checkIdempotency(String key, Object request) {
        return idempotencyService.find(key).map(record -> {
            String requestHash = idempotencyService.hash(serialize(request));
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(key);
            }
            return record.getResponseBody();
        });
    }

    private void saveIdempotency(String key, Object request, Object response) {
        idempotencyService.save(
                key,
                idempotencyService.hash(serialize(request)),
                serialize(response)
        );
    }

    private void saveAttempt(UUID paymentId, OperationType operation, ProviderResult result, long latencyMs, String providerName) {
        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .provider(providerName)
                .operation(operation)
                .status(result.status())
                .providerReference(result.providerReference())
                .errorCode(result.errorCode())
                .errorMessage(result.errorMessage())
                .latencyMs(latencyMs)
                .build();
        
        attemptRepository.save(attempt);
    }

    private void saveTransaction(UUID paymentId, OperationType type, BigDecimal amount, String providerReference) {
        TransactionEntity transaction = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .type(type)
                .amount(amount)
                .status("COMPLETED")
                .providerReference(providerReference)
                .build();
        
        transactionRepository.save(transaction);
    }

    private BigDecimal calculateTotalRefunded(UUID paymentId) {
        return transactionRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .filter(t -> t.getType() == OperationType.REFUND)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void publishEvent(PaymentEntity payment, String eventType) {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("Payment")
                .aggregateId(payment.getId())
                .eventType(eventType)
                .payload(serialize(toResponse(payment)))
                .status(OutboxStatus.NEW)
                .build();
        
        outboxEventRepository.save(event);
    }

    private PaymentResponse toResponse(PaymentEntity entity) {
        return PaymentResponse.builder()
                .id(entity.getId())
                .merchantId(entity.getMerchantId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .orderId(entity.getOrderId())
                .customerEmail(entity.getCustomerEmail())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .providerReference(entity.getProviderReference())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) {
            return "**@" + parts[1];
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }
}
