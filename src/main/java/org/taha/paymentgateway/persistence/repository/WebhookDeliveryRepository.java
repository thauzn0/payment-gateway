package org.taha.paymentgateway.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.taha.paymentgateway.core.model.WebhookStatus;
import org.taha.paymentgateway.persistence.entity.WebhookDeliveryEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {
    
    List<WebhookDeliveryEntity> findByEventId(UUID eventId);
    
    @Query("SELECT w FROM WebhookDeliveryEntity w WHERE w.status IN :statuses AND (w.nextRetryAt IS NULL OR w.nextRetryAt <= :now) ORDER BY w.createdAt ASC")
    List<WebhookDeliveryEntity> findPendingDeliveries(List<WebhookStatus> statuses, OffsetDateTime now);
}
