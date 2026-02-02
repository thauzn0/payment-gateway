package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Routing kuralları entity.
 * 
 * Hangi kart (BIN) + hangi merchant + hangi currency kombinasyonu
 * hangi provider'a (banka POS'una) yönlendirilecek ve komisyon oranı ne olacak.
 */
@Entity
@Table(name = "routing_rules", indexes = {
    @Index(name = "idx_routing_merchant", columnList = "merchant_id"),
    @Index(name = "idx_routing_currency", columnList = "currency")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingRuleEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(length = 3)
    private String currency;

    @Column(name = "card_bin_prefix", length = 6)
    private String cardBinPrefix;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    /** Komisyon oranı (örn: 0.99 = %0.99) */
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
