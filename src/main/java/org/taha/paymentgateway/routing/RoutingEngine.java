package org.taha.paymentgateway.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.taha.paymentgateway.persistence.entity.RoutingRuleEntity;
import org.taha.paymentgateway.persistence.repository.RoutingRuleRepository;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter.HealthStatus;
import org.taha.paymentgateway.routing.BinLookupService.BinInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider routing engine.
 * 
 * Kartın BIN numarasına ve işlem bilgilerine göre en uygun (düşük komisyonlu) 
 * sanal POS'u seçer:
 * 
 * 1. ON-US Routing: Kart hangi bankaya aitse, o bankanın POS'una yönlendir (düşük komisyon)
 * 2. Merchant Anlaşma: Merchant'ın özel anlaşması varsa ona yönlendir
 * 3. Currency Bazlı: TRY → Türk bankaları, USD/EUR → Uluslararası
 * 4. Fallback: Hiçbiri yoksa varsayılan provider
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingEngine {

    private final RoutingRuleRepository routingRuleRepository;
    private final Map<String, PaymentProviderAdapter> providerAdapters;
    private final BinLookupService binLookupService;

    /**
     * İşlem için en uygun (düşük komisyonlu) provider'ı seçer.
     * 
     * @param context Routing context (merchant, currency, cardBin vs.)
     * @return Routing sonucu (seçilen provider + neden seçildi)
     */
    public RoutingResult selectProvider(RoutingContext context) {
        log.info("=== ROUTING BAŞLIYOR ===");
        log.info("Merchant: {}, Currency: {}, BIN: {}", 
                context.merchantId(), context.currency(), maskBin(context.cardBin()));

        // BIN lookup yap
        Optional<BinInfo> binInfo = binLookupService.lookup(context.cardBin());
        if (binInfo.isPresent()) {
            log.info("BIN Bilgisi: {} - {} {} ({})", 
                    binInfo.get().binPrefix(),
                    binInfo.get().bankName(), 
                    binInfo.get().cardBrand(),
                    binInfo.get().cardType());
        } else {
            log.warn("BIN bulunamadı: {}", maskBin(context.cardBin()));
        }

        // DB'den eşleşen kuralları al (priority'ye göre sıralı)
        List<RoutingRuleEntity> rules = routingRuleRepository.findMatchingRules(
                context.merchantId(),
                context.currency(),
                context.cardBin()
        );

        log.info("Eşleşen {} kural bulundu", rules.size());

        // Eşleşen kuralları dene
        for (RoutingRuleEntity rule : rules) {
            log.debug("Kural deneniyor: {} (priority: {}, komisyon: {}%)", 
                    rule.getProviderName(), rule.getPriority(), rule.getCommissionRate());
            
            Optional<PaymentProviderAdapter> adapter = tryProvider(rule.getProviderName());
            if (adapter.isPresent()) {
                String reason = buildReason(rule, binInfo.orElse(null));
                log.info("✅ SEÇİLEN PROVIDER: {} - Komisyon: {}% - Sebep: {}", 
                        rule.getProviderName(), 
                        rule.getCommissionRate(),
                        reason);
                
                return new RoutingResult(
                        adapter.get(),
                        rule.getProviderName(),
                        rule.getCommissionRate(),
                        reason,
                        binInfo.orElse(null)
                );
            }
        }

        // Fallback: herhangi bir healthy provider
        Optional<PaymentProviderAdapter> fallback = providerAdapters.values().stream()
                .filter(this::isHealthy)
                .findFirst();

        if (fallback.isPresent()) {
            log.warn("⚠️ FALLBACK PROVIDER: {}", fallback.get().providerName());
            return new RoutingResult(
                    fallback.get(),
                    fallback.get().providerName(),
                    new BigDecimal("1.99"),
                    "Eşleşen kural bulunamadı, fallback kullanıldı",
                    binInfo.orElse(null)
            );
        }

        // En son çare: herhangi bir provider
        PaymentProviderAdapter anyProvider = providerAdapters.values().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment providers available"));

        log.error("❌ TÜM PROVIDER'LAR UNHEALTHY, zorunlu kullanım: {}", anyProvider.providerName());
        return new RoutingResult(
                anyProvider,
                anyProvider.providerName(),
                new BigDecimal("1.99"),
                "Tüm provider'lar unhealthy",
                binInfo.orElse(null)
        );
    }

    private String buildReason(RoutingRuleEntity rule, BinInfo binInfo) {
        StringBuilder reason = new StringBuilder();
        
        if (rule.getCardBinPrefix() != null && binInfo != null) {
            reason.append("ON-US: ").append(binInfo.bankName()).append(" kartı → ")
                  .append(rule.getProviderName()).append(" (düşük komisyon)");
        } else if (rule.getMerchantId() != null) {
            reason.append("Merchant anlaşması: ").append(rule.getMerchantId());
        } else if (rule.getCurrency() != null) {
            reason.append("Currency bazlı: ").append(rule.getCurrency());
        } else {
            reason.append("Varsayılan kural");
        }
        
        return reason.toString();
    }

    /**
     * Belirli bir provider'ı dene.
     * Healthy değilse Optional.empty döner.
     */
    private Optional<PaymentProviderAdapter> tryProvider(String providerName) {
        PaymentProviderAdapter adapter = providerAdapters.get(providerName);
        
        if (adapter == null) {
            log.warn("Provider not found: {}", providerName);
            return Optional.empty();
        }

        if (!isHealthy(adapter)) {
            log.warn("Provider unhealthy, skipping: {}", providerName);
            return Optional.empty();
        }

        return Optional.of(adapter);
    }

    private boolean isHealthy(PaymentProviderAdapter adapter) {
        try {
            HealthStatus status = adapter.healthCheck();
            return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
        } catch (Exception e) {
            log.error("Health check failed for provider: {}", adapter.providerName(), e);
            return false;
        }
    }

    private String maskBin(String bin) {
        if (bin == null || bin.length() < 4) {
            return "****";
        }
        return bin.substring(0, 4) + "**";
    }

    /**
     * Routing context record.
     */
    public record RoutingContext(
        String merchantId,
        String currency,
        String cardBin,
        String country
    ) {
        public static RoutingContext of(String merchantId, String currency, String cardBin) {
            return new RoutingContext(merchantId, currency, cardBin, null);
        }
    }

    /**
     * Routing sonucu.
     */
    public record RoutingResult(
        PaymentProviderAdapter provider,
        String providerName,
        BigDecimal commissionRate,
        String reason,
        BinInfo binInfo
    ) {}
}
