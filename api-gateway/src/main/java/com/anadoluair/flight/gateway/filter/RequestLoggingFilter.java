package com.anadoluair.flight.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter for request logging and correlation ID injection.
 * Every request gets a unique correlation ID for distributed tracing.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_TIME_ATTR = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(REQUEST_TIME_ATTR, startTime);

        ServerHttpRequest request = exchange.getRequest();

        // Generate or use existing correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Add correlation ID to request
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .header("X-Gateway-Request-Time", String.valueOf(startTime))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        String finalCorrelationId = correlationId;

        log.info(">>> Gateway Request: {} {} | CorrelationId: {} | Client: {}",
                request.getMethod(),
                request.getURI().getPath(),
                finalCorrelationId,
                request.getRemoteAddress());

        mutatedExchange.getResponse().beforeCommit(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = mutatedExchange.getResponse().getStatusCode() != null
                            ? mutatedExchange.getResponse().getStatusCode().value()
                            : 0;

                    log.info("<<< Gateway Response: {} {} | Status: {} | Duration: {}ms | CorrelationId: {}",
                            request.getMethod(),
                            request.getURI().getPath(),
                            statusCode,
                            duration,
                            finalCorrelationId);

                    // Response headers must be set before commit in WebFlux
                    mutatedExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
                    mutatedExchange.getResponse().getHeaders().set("X-Response-Time", duration + "ms");
                    return Mono.empty();
                });

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
