package org.taha.paymentgateway.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.taha.paymentgateway.persistence.entity.RoutingRuleEntity;
import org.taha.paymentgateway.persistence.repository.RoutingRuleRepository;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter.HealthStatus;
import org.taha.paymentgateway.routing.BinLookupService.BinInfo;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingContext;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingResult;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingEngine Tests")
class RoutingEngineTest {

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @Mock
    private BinLookupService binLookupService;

    @Mock
    private PaymentProviderAdapter mockProviderAdapter;

    @Mock
    private PaymentProviderAdapter fallbackProviderAdapter;

    private RoutingEngine routingEngine;
    private Map<String, PaymentProviderAdapter> providerAdapters;

    @BeforeEach
    void setUp() {
        providerAdapters = new HashMap<>();
        providerAdapters.put("MOCK_PROVIDER", mockProviderAdapter);
        providerAdapters.put("FALLBACK_PROVIDER", fallbackProviderAdapter);
        
        routingEngine = new RoutingEngine(routingRuleRepository, providerAdapters, binLookupService);
    }

    @Nested
    @DisplayName("selectProvider() tests")
    class SelectProviderTests {

        @Test
        @DisplayName("should select provider based on matching routing rule")
        void shouldSelectProvider_BasedOnMatchingRule() {
            // given
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            
            RoutingRuleEntity rule = createRoutingRule("MOCK_PROVIDER", 1, new BigDecimal("1.50"));
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.empty());
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(List.of(rule));
            when(mockProviderAdapter.providerName()).thenReturn("MOCK_PROVIDER");
            when(mockProviderAdapter.healthCheck()).thenReturn(HealthStatus.HEALTHY);

            // when
            RoutingResult result = routingEngine.selectProvider(context);

            // then
            assertThat(result.providerName()).isEqualTo("MOCK_PROVIDER");
            assertThat(result.provider()).isEqualTo(mockProviderAdapter);
            assertThat(result.commissionRate()).isEqualTo(new BigDecimal("1.50"));
        }

        @Test
        @DisplayName("should skip unhealthy provider and use next in list")
        void shouldSkipUnhealthyProvider_AndUseNext() {
            // given
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            
            RoutingRuleEntity unhealthyRule = createRoutingRule("MOCK_PROVIDER", 1, new BigDecimal("1.00"));
            RoutingRuleEntity healthyRule = createRoutingRule("FALLBACK_PROVIDER", 2, new BigDecimal("1.75"));
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.empty());
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(List.of(unhealthyRule, healthyRule));
            when(mockProviderAdapter.healthCheck()).thenReturn(HealthStatus.UNHEALTHY);
            when(fallbackProviderAdapter.providerName()).thenReturn("FALLBACK_PROVIDER");
            when(fallbackProviderAdapter.healthCheck()).thenReturn(HealthStatus.HEALTHY);

            // when
            RoutingResult result = routingEngine.selectProvider(context);

            // then
            assertThat(result.providerName()).isEqualTo("FALLBACK_PROVIDER");
            assertThat(result.commissionRate()).isEqualTo(new BigDecimal("1.75"));
        }

        @Test
        @DisplayName("should use fallback when no rules match")
        void shouldUseFallback_WhenNoRulesMatch() {
            // given
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.empty());
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(Collections.emptyList());
            when(mockProviderAdapter.healthCheck()).thenReturn(HealthStatus.HEALTHY);

            // when
            RoutingResult result = routingEngine.selectProvider(context);

            // then
            assertThat(result.reason()).contains("fallback");
        }

        @Test
        @DisplayName("should include BIN info in routing result when available")
        void shouldIncludeBinInfo_WhenAvailable() {
            // given
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            BinInfo binInfo = new BinInfo("415679", "Garanti BBVA", "VISA", "CREDIT", "TR");
            RoutingRuleEntity rule = createRoutingRule("MOCK_PROVIDER", 1, new BigDecimal("1.50"));
            rule.setCardBinPrefix("415679");
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.of(binInfo));
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(List.of(rule));
            when(mockProviderAdapter.providerName()).thenReturn("MOCK_PROVIDER");
            when(mockProviderAdapter.healthCheck()).thenReturn(HealthStatus.HEALTHY);

            // when
            RoutingResult result = routingEngine.selectProvider(context);

            // then
            assertThat(result.binInfo()).isNotNull();
            assertThat(result.binInfo().bankName()).isEqualTo("Garanti BBVA");
            assertThat(result.reason()).contains("ON-US");
        }

        @Test
        @DisplayName("should accept DEGRADED providers as healthy")
        void shouldAcceptDegradedProviders_AsHealthy() {
            // given
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            RoutingRuleEntity rule = createRoutingRule("MOCK_PROVIDER", 1, new BigDecimal("1.50"));
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.empty());
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(List.of(rule));
            when(mockProviderAdapter.providerName()).thenReturn("MOCK_PROVIDER");
            when(mockProviderAdapter.healthCheck()).thenReturn(HealthStatus.DEGRADED);

            // when
            RoutingResult result = routingEngine.selectProvider(context);

            // then
            assertThat(result.providerName()).isEqualTo("MOCK_PROVIDER");
        }

        @Test
        @DisplayName("should throw exception when no providers available")
        void shouldThrowException_WhenNoProvidersAvailable() {
            // given
            providerAdapters.clear();
            routingEngine = new RoutingEngine(routingRuleRepository, providerAdapters, binLookupService);
            
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");
            
            when(binLookupService.lookup("415679")).thenReturn(Optional.empty());
            when(routingRuleRepository.findMatchingRules("merchant-1", "TRY", "415679"))
                    .thenReturn(Collections.emptyList());

            // when/then
            assertThatThrownBy(() -> routingEngine.selectProvider(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No payment providers available");
        }
    }

    @Nested
    @DisplayName("RoutingContext tests")
    class RoutingContextTests {

        @Test
        @DisplayName("of() should create context with null country")
        void of_ShouldCreateContext_WithNullCountry() {
            // when
            RoutingContext context = RoutingContext.of("merchant-1", "TRY", "415679");

            // then
            assertThat(context.merchantId()).isEqualTo("merchant-1");
            assertThat(context.currency()).isEqualTo("TRY");
            assertThat(context.cardBin()).isEqualTo("415679");
            assertThat(context.country()).isNull();
        }

        @Test
        @DisplayName("constructor should accept all fields")
        void constructor_ShouldAcceptAllFields() {
            // when
            RoutingContext context = new RoutingContext("merchant-1", "USD", "400001", "US");

            // then
            assertThat(context.merchantId()).isEqualTo("merchant-1");
            assertThat(context.currency()).isEqualTo("USD");
            assertThat(context.cardBin()).isEqualTo("400001");
            assertThat(context.country()).isEqualTo("US");
        }
    }

    @Nested
    @DisplayName("RoutingResult tests")
    class RoutingResultTests {

        @Test
        @DisplayName("should store all routing result fields")
        void shouldStoreAllFields() {
            // given
            BinInfo binInfo = new BinInfo("415679", "Garanti", "VISA", "CREDIT", "TR");
            
            // when
            RoutingResult result = new RoutingResult(
                    mockProviderAdapter,
                    "MOCK_PROVIDER",
                    new BigDecimal("1.50"),
                    "ON-US routing",
                    binInfo
            );

            // then
            assertThat(result.provider()).isEqualTo(mockProviderAdapter);
            assertThat(result.providerName()).isEqualTo("MOCK_PROVIDER");
            assertThat(result.commissionRate()).isEqualTo(new BigDecimal("1.50"));
            assertThat(result.reason()).isEqualTo("ON-US routing");
            assertThat(result.binInfo()).isEqualTo(binInfo);
        }
    }

    private RoutingRuleEntity createRoutingRule(String providerName, int priority, BigDecimal commission) {
        RoutingRuleEntity rule = new RoutingRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setProviderName(providerName);
        rule.setPriority(priority);
        rule.setCommissionRate(commission);
        rule.setActive(true);
        return rule;
    }
}
