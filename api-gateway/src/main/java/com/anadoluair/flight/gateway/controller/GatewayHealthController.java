package com.anadoluair.flight.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway health and status controller.
 * Aggregates health status from all downstream services.
 */
@Slf4j
@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class GatewayHealthController {

    private final RouteLocator routeLocator;
    private final WebClient.Builder webClientBuilder;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> gatewayHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "api-gateway");
        health.put("timestamp", LocalDateTime.now().toString());

        return Mono.just(ResponseEntity.ok(health));
    }

    @GetMapping("/routes")
    public Mono<ResponseEntity<Map<String, Object>>> getRoutes() {
        return routeLocator.getRoutes()
                .map(route -> Map.of(
                        "id", route.getId(),
                        "uri", route.getUri().toString(),
                        "order", route.getOrder()
                ))
                .collectList()
                .map(routes -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("routes", routes);
                    response.put("count", routes.size());
                    response.put("timestamp", LocalDateTime.now().toString());
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping("/services/health")
    public Mono<ResponseEntity<Map<String, Object>>> aggregateHealth() {
        Map<String, String> services = Map.of(
                "booking-service", "http://booking-service:8083/actuator/health",
                "flight-ops-service", "http://flight-ops-service:8082/actuator/health",
                "integration-service", "http://integration-service:8081/actuator/health"
        );

        return Flux.fromIterable(services.entrySet())
                .flatMap(entry -> checkServiceHealth(entry.getKey(), entry.getValue()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(healthMap -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("gateway", "UP");
                    response.put("services", healthMap);
                    response.put("timestamp", LocalDateTime.now().toString());

                    // Overall status is DOWN if any service is DOWN
                    boolean allUp = healthMap.values().stream()
                            .allMatch(status -> "UP".equals(status));
                    response.put("overallStatus", allUp ? "UP" : "DEGRADED");

                    return ResponseEntity.ok(response);
                });
    }

    private Mono<Map.Entry<String, String>> checkServiceHealth(String serviceName, String healthUrl) {
        return webClientBuilder.build()
                .get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(response -> Map.entry(serviceName, "UP"))
                .onErrorReturn(Map.entry(serviceName, "DOWN"));
    }
}
