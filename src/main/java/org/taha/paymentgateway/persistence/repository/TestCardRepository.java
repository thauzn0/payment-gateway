package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.TestCardEntity;

import java.util.Optional;
import java.util.UUID;

public interface TestCardRepository extends JpaRepository<TestCardEntity, UUID> {
    
    Optional<TestCardEntity> findByCardNumber(String cardNumber);
    
    Optional<TestCardEntity> findByBinPrefix(String binPrefix);
    
    boolean existsByCardNumber(String cardNumber);
}
