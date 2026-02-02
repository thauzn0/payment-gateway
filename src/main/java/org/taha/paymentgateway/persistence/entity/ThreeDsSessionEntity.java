package org.taha.paymentgateway.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "three_ds_sessions", indexes = {
    @Index(name = "idx_3ds_payment", columnList = "payment_id"),
    @Index(name = "idx_3ds_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreeDsSessionEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "otp_code", nullable = false, length = 6)
    @Builder.Default
    private String otpCode = "111111";

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (expiresAt == null) expiresAt = OffsetDateTime.now().plusMinutes(5);
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }
}
