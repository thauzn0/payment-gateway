package org.taha.paymentgateway.core.exception;

import lombok.Getter;

/**
 * Payment işlemleri için base exception.
 */
@Getter
public class PaymentException extends RuntimeException {
    
    private final String errorCode;
    
    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
