package com.anadoluair.flight.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Anadolu Air API Gateway - Central entry point for all microservices.
 *
 * Features:
 * - Request routing to downstream services
 * - Rate limiting (Redis-based)
 * - Circuit breaker for fault tolerance
 * - Request/Response logging
 * - CORS configuration
 * - Authentication filter (JWT ready)
 * - Load balancing ready
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
