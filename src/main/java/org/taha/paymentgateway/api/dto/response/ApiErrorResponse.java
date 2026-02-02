package org.taha.paymentgateway.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    String errorCode,
    String message,
    List<FieldError> details,
    String traceId,
    OffsetDateTime timestamp
) {
    @Builder
    public record FieldError(
        String field,
        String message,
        Object rejectedValue
    ) {}
}
