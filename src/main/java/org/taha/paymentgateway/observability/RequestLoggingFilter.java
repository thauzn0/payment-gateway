package org.taha.paymentgateway.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Request/Response logging filter.
 * 
 * Her request'in başlangıç ve bitişini loglar.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.info(">>> {} {}", method, uri);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            
            if (status >= 500) {
                log.error("<<< {} {} - {} ({}ms)", method, uri, status, duration);
            } else if (status >= 400) {
                log.warn("<<< {} {} - {} ({}ms)", method, uri, status, duration);
            } else {
                log.info("<<< {} {} - {} ({}ms)", method, uri, status, duration);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health check ve actuator endpoint'lerini loglama
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
