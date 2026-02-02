package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.PaymentAttemptEntity;

import java.util.List;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    
    List<PaymentAttemptEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}