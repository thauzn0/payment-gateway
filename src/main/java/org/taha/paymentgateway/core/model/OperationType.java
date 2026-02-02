package org.taha.paymentgateway.core.model;

/**
 * Provider'a yapılan işlem türleri.
 */
public enum OperationType {
    /** Kartı yetkilendir, parayı bloke et */
    AUTHORIZE,
    
    /** Bloke edilen parayı çek */
    CAPTURE,
    
    /** Çekilen parayı iade et */
    REFUND,
    
    /** Authorize'u iptal et (capture öncesi) */
    VOID
}