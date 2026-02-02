package org.taha.paymentgateway.event;

import org.taha.paymentgateway.persistence.entity.OutboxEventEntity;

/**
 * Event handler interface.
 * Her event tipi için handler implement edilebilir.
 */
public interface EventHandler {
    
    /**
     * Bu handler bu event tipini işleyebilir mi?
     */
    boolean canHandle(String eventType);
    
    /**
     * Event'i işle.
     */
    void handle(OutboxEventEntity event);
}
