package org.taha.paymentgateway.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.taha.paymentgateway.api.dto.response.MetricsSummaryResponse;
import org.taha.paymentgateway.provider.mock.MockPaymentProviderAdapter;
import org.taha.paymentgateway.service.MetricsService;

/**
 * Admin API Controller.
 * 
 * Metrics ve yönetim endpoint'leri.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MetricsService metricsService;
    private final MockPaymentProviderAdapter mockProvider;

    /**
     * GET /v1/admin/metrics
     * Genel metrik özeti
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsSummaryResponse> getMetrics() {
        log.info("Admin metrics request");
        return ResponseEntity.ok(metricsService.getSummary());
    }

    /**
     * POST /v1/admin/mock-provider/mode
     * Mock provider modunu değiştirir (demo için)
     */
    @PostMapping("/mock-provider/mode")
    public ResponseEntity<String> setMockMode(@RequestParam String mode) {
        log.info("Setting mock provider mode to: {}", mode);
        
        try {
            MockPaymentProviderAdapter.MockMode mockMode = MockPaymentProviderAdapter.MockMode.valueOf(mode.toUpperCase());
            mockProvider.setMode(mockMode);
            return ResponseEntity.ok("Mock provider mode set to: " + mockMode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid mode. Valid modes: SUCCESS, FAIL, TIMEOUT, RANDOM, REQUIRES_3DS");
        }
    }

    /**
     * GET /v1/admin/mock-provider/mode
     * Mock provider modunu getirir
     */
    @GetMapping("/mock-provider/mode")
    public ResponseEntity<String> getMockMode() {
        return ResponseEntity.ok(mockProvider.getMode().name());
    }

    /**
     * GET /v1/admin/health
     * Basit health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
