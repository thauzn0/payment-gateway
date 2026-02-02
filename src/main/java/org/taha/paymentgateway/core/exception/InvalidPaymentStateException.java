package org.taha.paymentgateway.core.exception;

import org.taha.paymentgateway.core.model.PaymentStatus;

import java.util.UUID;

/**
 * Payment status'u işlem için uygun değilse fırlatılır.
 */
public class InvalidPaymentStateException extends PaymentException {
    
    public InvalidPaymentStateException(UUID paymentId, PaymentStatus currentStatus, String expectedStatus) {
        super("INVALID_PAYMENT_STATE", 
            String.format("Payment %s is in %s status, expected %s", paymentId, currentStatus, expectedStatus));
    }
}
