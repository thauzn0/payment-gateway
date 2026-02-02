package org.taha.paymentgateway.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record AuthorizePaymentRequest(
    @NotBlank(message = "Card token is required")
    @Size(max = 128, message = "Card token must not exceed 128 characters")
    String cardToken,
    
    @Size(min = 6, max = 6, message = "Card BIN must be 6 digits")
    String cardBin,
    
    /** 3DS tercihini belirtir: "required", "preferred", "disabled" */
    @Size(max = 16)
    String threeDsPreference
) {}
