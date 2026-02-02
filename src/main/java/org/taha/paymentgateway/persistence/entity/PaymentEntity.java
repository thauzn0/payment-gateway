package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.taha.paymentgateway.core.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_payments_order_id", columnList = "order_id"),
    @Index(name = "idx_payments_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "order_id", length = 128)
    private String orderId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(length = 500)
    private String description;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "requires_3ds", nullable = false)
    @Builder.Default
    private boolean requires3ds = false;

    @Column(name = "three_ds_session_id", columnDefinition = "BINARY(16)")
    private UUID threeDsSessionId;

    @Column(name = "card_bin", length = 6)
    private String cardBin;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}