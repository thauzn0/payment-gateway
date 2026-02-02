package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.taha.paymentgateway.persistence.entity.ApiLogEntity;

import java.util.List;
import java.util.UUID;

public interface ApiLogRepository extends JpaRepository<ApiLogEntity, UUID> {
    
    List<ApiLogEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    
    List<ApiLogEntity> findByCorrelationId(String correlationId);
    
    Page<ApiLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<ApiLogEntity> findTop100ByOrderByCreatedAtDesc();
}
