package org.taha.paymentgateway.core.exception;

/**
 * Provider'dan dönen hatalar için exception.
 */
public class ProviderException extends PaymentException {
    
    private final String providerName;
    
    public ProviderException(String providerName, String errorCode, String message) {
        super(errorCode, String.format("[%s] %s", providerName, message));
        this.providerName = providerName;
    }
    
    public String getProviderName() {
        return providerName;
    }
}
