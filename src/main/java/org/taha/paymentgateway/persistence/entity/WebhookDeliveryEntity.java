package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.taha.paymentgateway.core.model.WebhookStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", indexes = {
    @Index(name = "idx_webhook_status", columnList = "status"),
    @Index(name = "idx_webhook_next_retry", columnList = "next_retry_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.PENDING;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
