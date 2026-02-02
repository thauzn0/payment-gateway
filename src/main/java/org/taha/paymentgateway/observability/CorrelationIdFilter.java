package org.taha.paymentgateway.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation ID filter.
 * 
 * Her request'e benzersiz bir correlation-id atar.
 * Bu ID tüm loglar ve response header'ında görünür.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String MERCHANT_ID_MDC_KEY = "merchantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Correlation ID - header'dan al veya yeni oluştur
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            // MDC'ye ekle (tüm loglarda görünür)
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Merchant ID varsa ekle
            String merchantId = request.getHeader("X-Merchant-Id");
            if (merchantId != null && !merchantId.isBlank()) {
                MDC.put(MERCHANT_ID_MDC_KEY, merchantId);
            }

            // Response header'a ekle
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
