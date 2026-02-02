package org.taha.paymentgateway.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.taha.paymentgateway.persistence.entity.ApiLogEntity;
import org.taha.paymentgateway.persistence.repository.ApiLogRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogService {

    private final ApiLogRepository apiLogRepository;

    @Async
    public void logRequest(String correlationId, UUID paymentId, String method, String endpoint,
                          String headers, String body, Integer status, String response, Long latencyMs) {
        try {
            ApiLogEntity logEntity = ApiLogEntity.builder()
                    .id(UUID.randomUUID())
                    .correlationId(correlationId)
                    .paymentId(paymentId)
                    .httpMethod(method)
                    .endpoint(endpoint)
                    .requestHeaders(truncate(headers, 2000))
                    .requestBody(truncate(body, 5000))
                    .responseStatus(status)
                    .responseBody(truncate(response, 5000))
                    .latencyMs(latencyMs)
                    .build();

            apiLogRepository.save(logEntity);
            log.debug("API log saved: {} {} -> {}", method, endpoint, status);
        } catch (Exception e) {
            log.error("Failed to save API log", e);
        }
    }

    public List<ApiLogEntity> getRecentLogs() {
        return apiLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public List<ApiLogEntity> getLogsByPaymentId(UUID paymentId) {
        return apiLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
