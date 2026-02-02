package org.taha.paymentgateway.api.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record MetricsSummaryResponse(
    // Genel sayılar
    long totalPayments,
    long totalSuccessful,
    long totalFailed,
    
    // Status breakdown
    Map<String, Long> paymentsByStatus,
    
    // Provider metrikleri
    Map<String, ProviderMetrics> providerMetrics,
    
    // Zaman bazlı (son 24 saat)
    long paymentsLast24h,
    BigDecimal volumeLast24h,
    
    // Latency
    double avgLatencyMs,
    long maxLatencyMs
) {
    @Builder
    public record ProviderMetrics(
        long totalAttempts,
        long successCount,
        long failureCount,
        long timeoutCount,
        double successRate,
        double avgLatencyMs
    ) {}
}
