package com.anadoluair.flight.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic route configuration for complex routing scenarios.
 * Simple routes are configured in application.yml.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Admin routes with extra security headers
                .route("admin-bookings", r -> r
                        .path("/admin/bookings/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Admin-Request", "true")
                                .addRequestHeader("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                                .circuitBreaker(config -> config
                                        .setName("adminCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/admin"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("lb://booking-service"))

                // Health aggregation endpoint
                .route("health-aggregate", r -> r
                        .path("/health/all")
                        .filters(f -> f
                                .rewritePath("/health/all", "/actuator/health"))
                        .uri("lb://booking-service"))

                // Metrics aggregation
                .route("metrics-aggregate", r -> r
                        .path("/metrics/**")
                        .filters(f -> f
                                .rewritePath("/metrics/(?<service>.*)", "/actuator/prometheus"))
                        .uri("lb://booking-service"))

                // Flight search with caching hints
                .route("flight-search", r -> r
                        .path("/api/flights/search/**")
                        .filters(f -> f
                                .addResponseHeader("Cache-Control", "max-age=60")
                                .circuitBreaker(config -> config
                                        .setName("flightSearchCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/flights")))
                        .uri("lb://flight-ops-service"))

                .build();
    }
}
