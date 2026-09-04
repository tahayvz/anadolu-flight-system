package com.anadoluair.flight.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Authentication filter for validating API keys and JWT tokens.
 * In production, this would integrate with an identity provider.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/flights",           // Flight search is public
            "/api/bookings/health",   // Health checks
            "/actuator",              // Actuator endpoints
            "/fallback",              // Fallback endpoints
            "/swagger",               // API documentation
            "/v3/api-docs"            // OpenAPI docs
    );

    // Endpoints that require API key (B2B)
    private static final List<String> API_KEY_PATHS = List.of(
            "/api/integration"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Allow public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check API key for B2B endpoints
        if (isApiKeyPath(path)) {
            String apiKey = request.getHeaders().getFirst("X-API-Key");
            if (apiKey == null || !isValidApiKey(apiKey)) {
                log.warn("Invalid or missing API key for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(addUserContext(exchange, "api-client"));
        }

        // Check JWT token for authenticated endpoints
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // In production, validate JWT signature and claims
            if (isValidToken(token)) {
                String userId = extractUserId(token);
                return chain.filter(addUserContext(exchange, userId));
            }
        }

        // For demo purposes, allow all requests but log warning
        // In production, return 401 Unauthorized
        log.warn("Unauthenticated request to protected path: {} - allowing for demo", path);
        return chain.filter(addUserContext(exchange, "anonymous"));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isApiKeyPath(String path) {
        return API_KEY_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isValidApiKey(String apiKey) {
        // In production, validate against a key store
        // Demo keys for testing
        return apiKey.startsWith("anadolu-") && apiKey.length() >= 20;
    }

    private boolean isValidToken(String token) {
        // In production, validate JWT signature, expiry, issuer, etc.
        // For demo, accept any non-empty token
        return token != null && !token.isEmpty();
    }

    private String extractUserId(String token) {
        // In production, decode JWT and extract sub claim
        // For demo, use token hash as user ID
        return "user-" + Math.abs(token.hashCode() % 10000);
    }

    private ServerWebExchange addUserContext(ServerWebExchange exchange, String userId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-Authenticated", "true")
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // After logging filter
    }
}
