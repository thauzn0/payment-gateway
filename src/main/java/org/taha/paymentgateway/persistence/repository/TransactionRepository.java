package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.TransactionEntity;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    
    List<TransactionEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
