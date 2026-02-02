package org.taha.paymentgateway.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tüm API request/response'larını loglayan filter.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class ApiLogFilter implements Filter {

    private final ApiLogService apiLogService;
    
    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("/payments/([a-f0-9-]{36})");
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator", "/health", "/favicon.ico"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Static content ve health check'leri hariç tut
        if (shouldExclude(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Sadece API isteklerini logla
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest, 10 * 1024);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;

            try {
                logApiCall(wrappedRequest, wrappedResponse, latencyMs);
            } catch (Exception e) {
                log.error("Failed to log API call", e);
            }

            // Response'u client'a gönder
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logApiCall(ContentCachingRequestWrapper request, 
                           ContentCachingResponseWrapper response, 
                           long latencyMs) {
        String method = request.getMethod();
        String endpoint = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null) {
            endpoint += "?" + queryString;
        }

        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        // Request body
        String requestBody = getRequestBody(request);

        // Response body
        String responseBody = getResponseBody(response);

        // Headers (hassas bilgileri maskele)
        String headers = getHeaders(request);

        // Payment ID'yi çıkar
        UUID paymentId = extractPaymentId(endpoint);

        // Log kaydet
        apiLogService.logRequest(
                correlationId,
                paymentId,
                method,
                endpoint,
                headers,
                maskSensitiveData(requestBody),
                response.getStatus(),
                responseBody,
                latencyMs
        );
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }

    private String getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            // Hassas header'ları hariç tut
            if (!name.equalsIgnoreCase("authorization") && 
                !name.equalsIgnoreCase("cookie") &&
                !name.equalsIgnoreCase("x-api-key")) {
                headers.put(name, request.getHeader(name));
            }
        }
        return headers.toString();
    }

    private UUID extractPaymentId(String endpoint) {
        Matcher matcher = PAYMENT_ID_PATTERN.matcher(endpoint);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String maskSensitiveData(String body) {
        if (body == null) return null;
        
        // Kart numarası maskeleme
        body = body.replaceAll("\"cardNumber\"\\s*:\\s*\"(\\d{4})\\d{8}(\\d{4})\"", 
                               "\"cardNumber\":\"$1********$2\"");
        
        // CVV maskeleme
        body = body.replaceAll("\"cvv\"\\s*:\\s*\"\\d{3,4}\"", "\"cvv\":\"***\"");
        
        return body;
    }

    private boolean shouldExclude(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith) ||
               path.endsWith(".html") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".ico");
    }
}
