package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.taha.paymentgateway.core.model.AttemptStatus;
import org.taha.paymentgateway.core.model.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", indexes = {
    @Index(name = "idx_attempts_payment_id", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttemptEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationType operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttemptStatus status;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "request_masked", columnDefinition = "TEXT")
    private String requestMasked;

    @Column(name = "response_masked", columnDefinition = "TEXT")
    private String responseMasked;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}