package org.taha.paymentgateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.taha.paymentgateway.api.dto.response.MetricsSummaryResponse;
import org.taha.paymentgateway.api.dto.response.MetricsSummaryResponse.ProviderMetrics;
import org.taha.paymentgateway.core.model.AttemptStatus;
import org.taha.paymentgateway.core.model.PaymentStatus;
import org.taha.paymentgateway.persistence.entity.PaymentAttemptEntity;
import org.taha.paymentgateway.persistence.entity.PaymentEntity;
import org.taha.paymentgateway.persistence.repository.PaymentAttemptRepository;
import org.taha.paymentgateway.persistence.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metrics service.
 * 
 * Dashboard için metrik hesaplamaları yapar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;

    public MetricsSummaryResponse getSummary() {
        log.debug("Calculating metrics summary");

        List<PaymentEntity> allPayments = paymentRepository.findAll();
        List<PaymentAttemptEntity> allAttempts = attemptRepository.findAll();

        // Genel sayılar
        long totalPayments = allPayments.size();
        long totalSuccessful = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED || 
                            p.getStatus() == PaymentStatus.REFUNDED ||
                            p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED)
                .count();
        long totalFailed = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();

        // Status breakdown
        Map<String, Long> paymentsByStatus = allPayments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus().name(),
                        Collectors.counting()
                ));

        // Provider metrikleri
        Map<String, ProviderMetrics> providerMetrics = calculateProviderMetrics(allAttempts);

        // Son 24 saat
        OffsetDateTime last24h = OffsetDateTime.now().minusHours(24);
        List<PaymentEntity> recentPayments = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(last24h))
                .toList();
        
        long paymentsLast24h = recentPayments.size();
        BigDecimal volumeLast24h = recentPayments.stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Latency
        double avgLatencyMs = allAttempts.stream()
                .mapToLong(PaymentAttemptEntity::getLatencyMs)
                .average()
                .orElse(0);
        
        long maxLatencyMs = allAttempts.stream()
                .mapToLong(PaymentAttemptEntity::getLatencyMs)
                .max()
                .orElse(0);

        return MetricsSummaryResponse.builder()
                .totalPayments(totalPayments)
                .totalSuccessful(totalSuccessful)
                .totalFailed(totalFailed)
                .paymentsByStatus(paymentsByStatus)
                .providerMetrics(providerMetrics)
                .paymentsLast24h(paymentsLast24h)
                .volumeLast24h(volumeLast24h)
                .avgLatencyMs(avgLatencyMs)
                .maxLatencyMs(maxLatencyMs)
                .build();
    }

    private Map<String, ProviderMetrics> calculateProviderMetrics(List<PaymentAttemptEntity> attempts) {
        Map<String, List<PaymentAttemptEntity>> byProvider = attempts.stream()
                .collect(Collectors.groupingBy(PaymentAttemptEntity::getProvider));

        Map<String, ProviderMetrics> result = new HashMap<>();

        for (Map.Entry<String, List<PaymentAttemptEntity>> entry : byProvider.entrySet()) {
            String provider = entry.getKey();
            List<PaymentAttemptEntity> providerAttempts = entry.getValue();

            long total = providerAttempts.size();
            long success = providerAttempts.stream()
                    .filter(a -> a.getStatus() == AttemptStatus.SUCCESS)
                    .count();
            long failure = providerAttempts.stream()
                    .filter(a -> a.getStatus() == AttemptStatus.FAILURE)
                    .count();
            long timeout = providerAttempts.stream()
                    .filter(a -> a.getStatus() == AttemptStatus.TIMEOUT)
                    .count();

            double successRate = total > 0 ? (double) success / total * 100 : 0;
            double avgLatency = providerAttempts.stream()
                    .mapToLong(PaymentAttemptEntity::getLatencyMs)
                    .average()
                    .orElse(0);

            result.put(provider, ProviderMetrics.builder()
                    .totalAttempts(total)
                    .successCount(success)
                    .failureCount(failure)
                    .timeoutCount(timeout)
                    .successRate(Math.round(successRate * 100.0) / 100.0)
                    .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                    .build());
        }

        return result;
    }
}
