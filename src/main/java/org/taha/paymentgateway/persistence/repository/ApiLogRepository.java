package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.taha.paymentgateway.persistence.entity.ApiLogEntity;

import java.util.List;
import java.util.UUID;

public interface ApiLogRepository extends JpaRepository<ApiLogEntity, UUID> {
    
    List<ApiLogEntity> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    
    List<ApiLogEntity> findByCorrelationId(String correlationId);
    
    Page<ApiLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<ApiLogEntity> findTop100ByOrderByCreatedAtDesc();
    
    // Count methods for stats
    long countByResponseStatusBetween(int startStatus, int endStatus);
    
    long countByResponseStatusGreaterThanEqual(int status);
    
    @Query("SELECT AVG(a.latencyMs) FROM ApiLogEntity a WHERE a.latencyMs IS NOT NULL")
    Double getAverageLatency();
    
    @Query("SELECT a.latencyMs FROM ApiLogEntity a WHERE a.latencyMs IS NOT NULL ORDER BY a.latencyMs")
    List<Long> getAllLatenciesSorted();
}
