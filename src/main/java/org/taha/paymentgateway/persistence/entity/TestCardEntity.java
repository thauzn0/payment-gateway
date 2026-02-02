package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "test_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCardEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "card_number", nullable = false, unique = true, length = 19)
    private String cardNumber;

    @Column(name = "card_holder", nullable = false, length = 100)
    private String cardHolder;

    @Column(name = "expiry_month", nullable = false, length = 2)
    private String expiryMonth;

    @Column(name = "expiry_year", nullable = false, length = 4)
    private String expiryYear;

    @Column(nullable = false, length = 4)
    private String cvv;

    @Column(name = "bank_name", nullable = false, length = 64)
    private String bankName;

    @Column(name = "card_brand", nullable = false, length = 32)
    private String cardBrand;

    @Column(name = "bin_prefix", nullable = false, length = 6)
    private String binPrefix;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "should_fail", nullable = false)
    private boolean shouldFail;

    @Column(name = "fail_reason", length = 100)
    private String failReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
