package org.taha.paymentgateway.core.model;

/**
 * Outbox event durumları.
 */
public enum OutboxStatus {
    /** Yeni oluşturuldu, henüz işlenmedi */
    NEW,
    
    /** Başarıyla gönderildi/işlendi */
    SENT,
    
    /** Gönderim başarısız oldu */
    FAILED
}
