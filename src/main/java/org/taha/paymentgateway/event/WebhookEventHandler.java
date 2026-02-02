package org.taha.paymentgateway.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.taha.paymentgateway.core.model.WebhookStatus;
import org.taha.paymentgateway.persistence.entity.MerchantConfigEntity;
import org.taha.paymentgateway.persistence.entity.OutboxEventEntity;
import org.taha.paymentgateway.persistence.entity.WebhookDeliveryEntity;
import org.taha.paymentgateway.persistence.repository.MerchantConfigRepository;
import org.taha.paymentgateway.persistence.repository.WebhookDeliveryRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Payment event'lerini webhook delivery'ye dönüştürür.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventHandler implements EventHandler {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "PaymentCreated",
            "PaymentAuthorized",
            "PaymentCaptured",
            "PaymentRefunded",
            "PaymentFailed"
    );

    private final MerchantConfigRepository merchantConfigRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String eventType) {
        return SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public void handle(OutboxEventEntity event) {
        log.info("Creating webhook delivery for event - type: {}, aggregateId: {}", 
                event.getEventType(), event.getAggregateId());

        // Payload'dan merchantId çıkar
        String merchantId = extractMerchantId(event.getPayload());
        if (merchantId == null) {
            log.warn("Could not extract merchantId from event payload");
            return;
        }

        // Merchant config'den webhook URL al
        Optional<MerchantConfigEntity> merchantConfig = merchantConfigRepository.findByMerchantId(merchantId);
        if (merchantConfig.isEmpty() || merchantConfig.get().getWebhookUrl() == null) {
            log.debug("No webhook URL configured for merchant: {}", merchantId);
            return;
        }

        MerchantConfigEntity config = merchantConfig.get();

        // Webhook payload oluştur
        String webhookPayload = buildWebhookPayload(event);

        // Webhook delivery kaydı oluştur
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .id(UUID.randomUUID())
                .eventId(event.getId())
                .merchantId(merchantId)
                .targetUrl(config.getWebhookUrl())
                .payload(webhookPayload)
                .status(WebhookStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        webhookDeliveryRepository.save(delivery);
        log.info("Webhook delivery created - id: {}, url: {}", delivery.getId(), config.getWebhookUrl());
    }

    private String extractMerchantId(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode merchantIdNode = node.get("merchantId");
            return merchantIdNode != null ? merchantIdNode.asText() : null;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse event payload", e);
            return null;
        }
    }

    private String buildWebhookPayload(OutboxEventEntity event) {
        try {
            return objectMapper.writeValueAsString(new WebhookPayload(
                    event.getId().toString(),
                    event.getEventType(),
                    event.getPayload(),
                    event.getCreatedAt().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build webhook payload", e);
        }
    }

    record WebhookPayload(String eventId, String eventType, String data, String timestamp) {}
}
