package org.taha.paymentgateway.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record RefundPaymentRequest(
    /** 
     * Opsiyonel: Kısmi iade için tutar.
     * Belirtilmezse tam iade yapılır.
     */
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount,
    
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    String reason
) {}
