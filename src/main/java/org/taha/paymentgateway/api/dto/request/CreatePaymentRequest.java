package org.taha.paymentgateway.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CreatePaymentRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount,
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency,
    
    @Size(max = 128, message = "Order ID must not exceed 128 characters")
    String orderId,
    
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Customer email must not exceed 255 characters")
    String customerEmail,
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description
) {}
