package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.taha.paymentgateway.persistence.entity.RoutingRuleEntity;

import java.util.List;
import java.util.UUID;

public interface RoutingRuleRepository extends JpaRepository<RoutingRuleEntity, UUID> {
    
    @Query("""
        SELECT r FROM RoutingRuleEntity r 
        WHERE r.isActive = true 
        AND (r.merchantId IS NULL OR r.merchantId = :merchantId)
        AND (r.currency IS NULL OR r.currency = :currency)
        AND (r.cardBinPrefix IS NULL OR :cardBin LIKE CONCAT(r.cardBinPrefix, '%'))
        ORDER BY r.priority DESC
        """)
    List<RoutingRuleEntity> findMatchingRules(String merchantId, String currency, String cardBin);
    
    List<RoutingRuleEntity> findByMerchantIdAndIsActiveTrueOrderByPriorityDesc(String merchantId);
}
