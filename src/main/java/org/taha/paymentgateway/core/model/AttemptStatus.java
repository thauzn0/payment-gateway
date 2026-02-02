package org.taha.paymentgateway.core.model;

/**
 * Provider'a yapılan her bir denemenin sonucu.
 */
public enum AttemptStatus {
    /** İşlem başarılı */
    SUCCESS,
    
    /** İşlem başarısız (provider red verdi) */
    FAILURE,
    
    /** Provider'dan cevap alınamadı */
    TIMEOUT,
    
    /** 3D Secure doğrulaması gerekli */
    REQUIRES_3DS,
    
    /** İşlem devam ediyor (async durumlar için) */
    PENDING
}