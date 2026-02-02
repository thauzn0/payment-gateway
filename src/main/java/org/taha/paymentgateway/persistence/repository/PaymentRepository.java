package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.PaymentEntity;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
}