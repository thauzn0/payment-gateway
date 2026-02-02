package org.taha.paymentgateway.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider adapter'ları registry'e kaydeder.
 * Her adapter providerName() ile map'e eklenir.
 */
@Configuration
public class ProviderRegistry {

    @Bean
    public Map<String, PaymentProviderAdapter> providerAdapters(List<PaymentProviderAdapter> adapters) {
        return adapters.stream()
                .collect(Collectors.toMap(
                        PaymentProviderAdapter::providerName,
                        Function.identity()
                ));
    }
}
