package org.taha.paymentgateway.provider.adapter;

import org.taha.paymentgateway.core.model.AttemptStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Her provider'ın implement etmesi gereken adapter arayüzü.
 * Gateway'in internal modelini provider'ın API'sine uyarlar.
 */
public interface PaymentProviderAdapter {

    /**
     * Provider'ın benzersiz adı.
     */
    String providerName();

    /**
     * Kartı authorize et (parayı bloke et).
     */
    ProviderResult authorize(AuthorizeContext context);

    /**
     * Authorized tutarı capture et (parayı çek).
     */
    ProviderResult capture(CaptureContext context);

    /**
     * Captured tutarı iade et.
     */
    ProviderResult refund(RefundContext context);

    /**
     * Provider'ın sağlık durumunu kontrol et.
     */
    HealthStatus healthCheck();

    // ========== Context Records ==========
    
    record AuthorizeContext(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String cardToken,
        String cardBin,
        String merchantId
    ) {}

    record CaptureContext(
        UUID paymentId,
        String providerReference,
        BigDecimal amount,
        String currency
    ) {}

    record RefundContext(
        UUID paymentId,
        String providerReference,
        BigDecimal amount,
        String currency,
        String reason
    ) {}

    // ========== Result Records ==========
    
    record ProviderResult(
        AttemptStatus status,
        String providerReference,
        String errorCode,
        String errorMessage,
        boolean requires3DS,
        String threeDSUrl
    ) {
        public static ProviderResult success(String providerReference) {
            return new ProviderResult(AttemptStatus.SUCCESS, providerReference, null, null, false, null);
        }

        public static ProviderResult failure(String errorCode, String errorMessage) {
            return new ProviderResult(AttemptStatus.FAILURE, null, errorCode, errorMessage, false, null);
        }

        public static ProviderResult timeout() {
            return new ProviderResult(AttemptStatus.TIMEOUT, null, "TIMEOUT", "Provider did not respond in time", false, null);
        }

        public static ProviderResult requires3DS(String threeDSUrl) {
            return new ProviderResult(AttemptStatus.REQUIRES_3DS, null, null, null, true, threeDSUrl);
        }
    }

    enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
}