package org.taha.paymentgateway.api.dto.response;

import lombok.Builder;
import org.taha.paymentgateway.core.model.AttemptStatus;
import org.taha.paymentgateway.core.model.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentAttemptResponse(
    UUID id,
    UUID paymentId,
    String provider,
    OperationType operation,
    AttemptStatus status,
    String providerReference,
    String errorCode,
    String errorMessage,
    long latencyMs,
    OffsetDateTime createdAt
) {}
