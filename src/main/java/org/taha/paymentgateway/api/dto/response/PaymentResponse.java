package org.taha.paymentgateway.api.dto.response;

import lombok.Builder;
import org.taha.paymentgateway.core.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
    UUID id,
    String merchantId,
    BigDecimal amount,
    String currency,
    String orderId,
    String customerEmail,
    String description,
    PaymentStatus status,
    String providerReference,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
