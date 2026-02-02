package org.taha.paymentgateway.core.exception;

import java.util.UUID;

/**
 * Payment bulunamadığında fırlatılır.
 */
public class PaymentNotFoundException extends PaymentException {
    
    public PaymentNotFoundException(UUID paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId);
    }
}
