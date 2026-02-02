package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.taha.paymentgateway.core.model.OperationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_payment_id", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
