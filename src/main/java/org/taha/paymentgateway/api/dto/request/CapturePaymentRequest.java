package org.taha.paymentgateway.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CapturePaymentRequest(
    /** 
     * Opsiyonel: Kısmi capture için tutar.
     * Belirtilmezse tüm authorized tutar capture edilir.
     */
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount
) {}
