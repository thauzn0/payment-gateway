package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.ThreeDsSessionEntity;

import java.util.Optional;
import java.util.UUID;

public interface ThreeDsSessionRepository extends JpaRepository<ThreeDsSessionEntity, UUID> {
    
    Optional<ThreeDsSessionEntity> findByPaymentId(UUID paymentId);
    
    Optional<ThreeDsSessionEntity> findByPaymentIdAndStatus(UUID paymentId, String status);
}
