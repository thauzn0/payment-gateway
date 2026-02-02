package org.taha.paymentgateway.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.taha.paymentgateway.core.model.WebhookStatus;
import org.taha.paymentgateway.persistence.entity.MerchantConfigEntity;
import org.taha.paymentgateway.persistence.entity.WebhookDeliveryEntity;
import org.taha.paymentgateway.persistence.repository.MerchantConfigRepository;
import org.taha.paymentgateway.persistence.repository.WebhookDeliveryRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Webhook dispatcher.
 * 
 * Pending webhook'ları merchant endpoint'lerine gönderir.
 * Exponential backoff ile retry yapar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final int MAX_RETRIES = 5;
    private static final int[] BACKOFF_SECONDS = {0, 30, 120, 600, 3600}; // 0, 30s, 2m, 10m, 1h

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final MerchantConfigRepository merchantConfigRepository;
    private final RestTemplate restTemplate;

    @Value("${webhook.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Her 10 saniyede bir pending webhook'ları gönder.
     */
    @Scheduled(fixedDelayString = "${webhook.poll-interval-ms:10000}")
    public void dispatchWebhooks() {
        List<WebhookDeliveryEntity> pendingDeliveries = webhookDeliveryRepository
                .findPendingDeliveries(
                        List.of(WebhookStatus.PENDING, WebhookStatus.FAILED),
                        OffsetDateTime.now()
                );

        if (pendingDeliveries.isEmpty()) {
            return;
        }

        log.debug("Dispatching {} webhooks", pendingDeliveries.size());

        for (WebhookDeliveryEntity delivery : pendingDeliveries) {
            dispatchWebhook(delivery);
        }
    }

    @Transactional
    public void dispatchWebhook(WebhookDeliveryEntity delivery) {
        log.info("Dispatching webhook - id: {}, url: {}, attempt: {}", 
                delivery.getId(), delivery.getTargetUrl(), delivery.getRetryCount() + 1);

        try {
            // HMAC signature oluştur
            String signature = generateSignature(delivery);

            // HTTP request hazırla
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Id", delivery.getId().toString());
            headers.set("X-Webhook-Signature", signature);
            headers.set("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis()));

            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);

            // Gönder
            ResponseEntity<String> response = restTemplate.exchange(
                    delivery.getTargetUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // Response'u kaydet
            delivery.setResponseCode(response.getStatusCode().value());
            delivery.setResponseBody(truncate(response.getBody(), 1000));
            delivery.setLastAttemptAt(OffsetDateTime.now());

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus(WebhookStatus.DELIVERED);
                log.info("Webhook delivered successfully - id: {}", delivery.getId());
            } else {
                handleFailure(delivery, "Non-2xx response: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Webhook delivery failed - id: {}, error: {}", delivery.getId(), e.getMessage());
            handleFailure(delivery, e.getMessage());
        }

        webhookDeliveryRepository.save(delivery);
    }

    private void handleFailure(WebhookDeliveryEntity delivery, String errorMessage) {
        delivery.setRetryCount(delivery.getRetryCount() + 1);
        delivery.setLastAttemptAt(OffsetDateTime.now());
        delivery.setResponseBody(truncate(errorMessage, 1000));

        if (delivery.getRetryCount() >= MAX_RETRIES) {
            delivery.setStatus(WebhookStatus.EXHAUSTED);
            log.error("Webhook exhausted retries - id: {}", delivery.getId());
        } else {
            delivery.setStatus(WebhookStatus.FAILED);
            // Exponential backoff
            int backoffIndex = Math.min(delivery.getRetryCount(), BACKOFF_SECONDS.length - 1);
            delivery.setNextRetryAt(OffsetDateTime.now().plusSeconds(BACKOFF_SECONDS[backoffIndex]));
            log.warn("Webhook will be retried - id: {}, nextRetry: {}", 
                    delivery.getId(), delivery.getNextRetryAt());
        }
    }

    private String generateSignature(WebhookDeliveryEntity delivery) {
        // Merchant'ın webhook secret'ını al
        String secret = merchantConfigRepository.findByMerchantId(delivery.getMerchantId())
                .map(MerchantConfigEntity::getWebhookSecret)
                .orElse("default-secret");

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            
            String payload = delivery.getPayload() + "." + System.currentTimeMillis();
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to generate webhook signature", e);
            return "";
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
