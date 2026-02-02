package org.taha.paymentgateway.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.taha.paymentgateway.core.model.OutboxStatus;
import org.taha.paymentgateway.persistence.entity.OutboxEventEntity;
import org.taha.paymentgateway.persistence.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outbox event processor.
 * 
 * Periyodik olarak outbox tablosunu tarar ve
 * NEW statüsündeki eventleri işler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxProcessor {

    private static final int MAX_RETRIES = 3;
    
    private final OutboxEventRepository outboxEventRepository;
    private final List<EventHandler> eventHandlers;

    /**
     * Her 5 saniyede bir outbox'ı tarar.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void processOutbox() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findPendingEvents(OutboxStatus.NEW, MAX_RETRIES);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox events", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            processEvent(event);
        }
    }

    @Transactional
    public void processEvent(OutboxEventEntity event) {
        try {
            log.info("Processing outbox event - id: {}, type: {}, aggregateId: {}", 
                    event.getId(), event.getEventType(), event.getAggregateId());

            // İlgili handler'lara gönder
            for (EventHandler handler : eventHandlers) {
                if (handler.canHandle(event.getEventType())) {
                    handler.handle(event);
                }
            }

            // Başarılı - SENT olarak işaretle
            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(OffsetDateTime.now());
            outboxEventRepository.save(event);

            log.info("Outbox event processed successfully - id: {}", event.getId());

        } catch (Exception e) {
            log.error("Failed to process outbox event - id: {}", event.getId(), e);
            
            event.setRetryCount(event.getRetryCount() + 1);
            
            if (event.getRetryCount() >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event exhausted retries - id: {}", event.getId());
            }
            
            outboxEventRepository.save(event);
        }
    }
}
