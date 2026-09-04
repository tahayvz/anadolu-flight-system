package com.anadoluair.flight.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback controller for circuit breaker scenarios.
 * Provides graceful degradation when downstream services are unavailable.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> bookingsFallback() {
        log.warn("Booking service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service Temporarily Unavailable",
                        "message", "Booking service is currently unavailable. Please try again later.",
                        "service", "booking-service",
                        "timestamp", LocalDateTime.now().toString(),
                        "retryAfter", 30,
                        "supportContact", "support@anadoluair.example"
                ));
    }

    @GetMapping("/flights")
    public ResponseEntity<Map<String, Object>> flightsFallback() {
        log.warn("Flight ops service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service Temporarily Unavailable",
                        "message", "Flight information service is currently unavailable. Please try again later.",
                        "service", "flight-ops-service",
                        "timestamp", LocalDateTime.now().toString(),
                        "retryAfter", 30,
                        "cachedDataAvailable", false
                ));
    }

    @GetMapping("/integration")
    public ResponseEntity<Map<String, Object>> integrationFallback() {
        log.warn("Integration service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service Temporarily Unavailable",
                        "message", "Integration service is currently unavailable. Events will be processed when service recovers.",
                        "service", "integration-service",
                        "timestamp", LocalDateTime.now().toString(),
                        "retryAfter", 60,
                        "queueStatus", "Events are being queued for later processing"
                ));
    }

    @GetMapping("/admin")
    public ResponseEntity<Map<String, Object>> adminFallback() {
        log.warn("Admin endpoint fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Admin Service Unavailable",
                        "message", "Administrative functions are temporarily unavailable.",
                        "timestamp", LocalDateTime.now().toString(),
                        "escalation", "Contact system administrator"
                ));
    }

    @GetMapping("/default")
    public ResponseEntity<Map<String, Object>> defaultFallback() {
        log.warn("Default fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service Unavailable",
                        "message", "The requested service is currently unavailable. Our team has been notified.",
                        "timestamp", LocalDateTime.now().toString(),
                        "retryAfter", 30
                ));
    }
}
