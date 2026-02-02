package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.taha.paymentgateway.core.model.OutboxStatus;
import org.taha.paymentgateway.persistence.entity.OutboxEventEntity;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
    
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = :status AND e.retryCount < :maxRetries ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents(OutboxStatus status, int maxRetries);
}
