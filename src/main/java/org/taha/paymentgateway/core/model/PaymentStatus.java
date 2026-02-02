package org.taha.paymentgateway.core.model;

/**
 * Payment yaşam döngüsündeki durumlar.
 * 
 * Geçiş akışı:
 * CREATED -> AUTHORIZED -> CAPTURED -> REFUNDED
 *         -> FAILED (herhangi bir adımda hata olursa)
 */
public enum PaymentStatus {
    /** Ödeme oluşturuldu, henüz authorize edilmedi */
    CREATED,
    
    /** Kart authorize edildi, para bloke edildi */
    AUTHORIZED,
    
    /** Para çekildi (capture yapıldı) */
    CAPTURED,
    
    /** Tam iade yapıldı */
    REFUNDED,
    
    /** Kısmi iade yapıldı */
    PARTIALLY_REFUNDED,
    
    /** İşlem başarısız oldu */
    FAILED,
    
    /** İşlem iptal edildi (void - authorize sonrası capture öncesi) */
    CANCELLED
}