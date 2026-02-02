package org.taha.paymentgateway.provider.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test ve demo amaçlı mock provider adapter.
 * 
 * Farklı davranışları simüle edebilir:
 * - SUCCESS: Her zaman başarılı
 * - FAIL: Her zaman başarısız
 * - TIMEOUT: Her zaman timeout
 * - RANDOM: Rastgele başarılı/başarısız
 * - REQUIRES_3DS: 3D Secure gerektir
 */
@Slf4j
@Component
public class MockPaymentProviderAdapter implements PaymentProviderAdapter {

    public enum MockMode {
        SUCCESS,
        FAIL,
        TIMEOUT,
        RANDOM,
        REQUIRES_3DS
    }

    private final AtomicReference<MockMode> currentMode = new AtomicReference<>(MockMode.SUCCESS);
    
    @Value("${provider.mock.latency-ms:100}")
    private long simulatedLatencyMs;

    @Override
    public String providerName() {
        return "MOCK_PROVIDER";
    }

    /**
     * Mock modunu değiştirir (demo sırasında kullanılabilir).
     */
    public void setMode(MockMode mode) {
        log.info("MockProvider mode changed to: {}", mode);
        this.currentMode.set(mode);
    }

    public MockMode getMode() {
        return currentMode.get();
    }

    @Override
    public ProviderResult authorize(AuthorizeContext context) {
        log.info("MockProvider authorize called - paymentId: {}, amount: {}, mode: {}", 
                context.paymentId(), context.amount(), currentMode.get());
        
        simulateLatency();
        
        return switch (currentMode.get()) {
            case SUCCESS -> ProviderResult.success("MOCK-AUTH-" + UUID.randomUUID().toString().substring(0, 8));
            case FAIL -> ProviderResult.failure("MOCK_DECLINED", "Card declined by issuer");
            case TIMEOUT -> ProviderResult.timeout();
            case RANDOM -> Math.random() > 0.3 
                    ? ProviderResult.success("MOCK-AUTH-" + UUID.randomUUID().toString().substring(0, 8))
                    : ProviderResult.failure("MOCK_RANDOM_FAIL", "Random failure for testing");
            case REQUIRES_3DS -> ProviderResult.requires3DS("https://mock-3ds.example.com/verify?id=" + context.paymentId());
        };
    }

    @Override
    public ProviderResult capture(CaptureContext context) {
        log.info("MockProvider capture called - paymentId: {}, amount: {}, providerRef: {}", 
                context.paymentId(), context.amount(), context.providerReference());
        
        simulateLatency();
        
        return switch (currentMode.get()) {
            case SUCCESS, RANDOM -> ProviderResult.success("MOCK-CAP-" + UUID.randomUUID().toString().substring(0, 8));
            case FAIL -> ProviderResult.failure("MOCK_CAPTURE_FAILED", "Capture failed");
            case TIMEOUT -> ProviderResult.timeout();
            case REQUIRES_3DS -> ProviderResult.success("MOCK-CAP-" + UUID.randomUUID().toString().substring(0, 8));
        };
    }

    @Override
    public ProviderResult refund(RefundContext context) {
        log.info("MockProvider refund called - paymentId: {}, amount: {}, reason: {}", 
                context.paymentId(), context.amount(), context.reason());
        
        simulateLatency();
        
        return switch (currentMode.get()) {
            case SUCCESS, RANDOM -> ProviderResult.success("MOCK-REF-" + UUID.randomUUID().toString().substring(0, 8));
            case FAIL -> ProviderResult.failure("MOCK_REFUND_FAILED", "Refund failed");
            case TIMEOUT -> ProviderResult.timeout();
            case REQUIRES_3DS -> ProviderResult.success("MOCK-REF-" + UUID.randomUUID().toString().substring(0, 8));
        };
    }

    @Override
    public HealthStatus healthCheck() {
        return switch (currentMode.get()) {
            case TIMEOUT -> HealthStatus.UNHEALTHY;
            case FAIL -> HealthStatus.DEGRADED;
            default -> HealthStatus.HEALTHY;
        };
    }

    private void simulateLatency() {
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}