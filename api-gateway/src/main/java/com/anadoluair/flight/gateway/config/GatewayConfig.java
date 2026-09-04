package com.anadoluair.flight.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Gateway configuration for rate limiting key resolvers.
 * Provides different strategies for identifying clients for rate limiting.
 */
@Configuration
public class GatewayConfig {

    /**
     * Rate limit by client IP address.
     * Used for anonymous/public endpoints.
     * This is the default (primary) key resolver.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown"
        );
    }

    /**
     * Rate limit by API key header.
     * Used for B2B integrations.
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getHeaders().getFirst("X-API-Key") != null
                        ? exchange.getRequest().getHeaders().getFirst("X-API-Key")
                        : "anonymous"
        );
    }

    /**
     * Rate limit by user ID from JWT token.
     * Used for authenticated endpoints.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // In production, decode JWT and extract user ID
                // For now, use the token itself as key
                return Mono.just(authHeader.substring(7, Math.min(authHeader.length(), 50)));
            }
            return Mono.just("anonymous");
        };
    }
}
