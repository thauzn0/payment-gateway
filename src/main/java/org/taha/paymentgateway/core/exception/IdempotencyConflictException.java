package org.taha.paymentgateway.core.exception;

/**
 * Aynı idempotency key ile farklı request geldiğinde fırlatılır.
 */
public class IdempotencyConflictException extends PaymentException {
    
    public IdempotencyConflictException(String key) {
        super("IDEMPOTENCY_CONFLICT", 
            "Idempotency key already used with different request body: " + key);
    }
}
