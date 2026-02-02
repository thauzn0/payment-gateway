package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.MerchantConfigEntity;

import java.util.Optional;
import java.util.UUID;

public interface MerchantConfigRepository extends JpaRepository<MerchantConfigEntity, UUID> {
    
    Optional<MerchantConfigEntity> findByMerchantId(String merchantId);
    
    Optional<MerchantConfigEntity> findByApiKey(String apiKey);
    
    boolean existsByMerchantId(String merchantId);
}
