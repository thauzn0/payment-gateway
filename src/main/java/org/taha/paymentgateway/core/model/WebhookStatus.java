package org.taha.paymentgateway.core.model;

/**
 * Webhook delivery durumları.
 */
public enum WebhookStatus {
    /** Henüz gönderilmedi */
    PENDING,
    
    /** Başarıyla teslim edildi (2xx response) */
    DELIVERED,
    
    /** Teslim edilemedi, yeniden denenecek */
    FAILED,
    
    /** Maksimum deneme sayısına ulaşıldı */
    EXHAUSTED
}
