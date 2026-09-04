package com.anadoluair.flight.bookingservice.client;

import com.anadoluair.flight.bookingservice.config.RedisCacheConfig;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST client for communicating with flight-ops-service.
 * Uses Resilience4j for fault tolerance:
 * - Circuit Breaker: Prevents cascading failures
 * - Retry: Automatically retries failed requests
 * - Bulkhead: Limits concurrent calls
 * - Rate Limiter: Prevents overloading the service
 */
@Slf4j
@Component
public class FlightOpsClient {

    private static final String FLIGHT_OPS_CB = "flightOpsService";

    private final RestTemplate restTemplate;
    private final String flightOpsBaseUrl;

    public FlightOpsClient(
            RestTemplate restTemplate,
            @Value("${flight-ops.base-url:http://localhost:8082}") String flightOpsBaseUrl) {
        this.restTemplate = restTemplate;
        this.flightOpsBaseUrl = flightOpsBaseUrl;
    }

    /**
     * Get flight booking information with circuit breaker protection.
     * Cached for 15 minutes - flight info doesn't change frequently.
     * Anadolu Air Scenario: Same flight queried by multiple booking agents.
     *
     * Resilience4j annotation order: CircuitBreaker -> Retry -> Bulkhead -> RateLimiter
     * Note: Cache is checked BEFORE circuit breaker, reducing load on flight-ops-service.
     */
    @Cacheable(value = RedisCacheConfig.FLIGHT_CACHE, key = "'flight_info_' + #flightNumber.toUpperCase()")
    @CircuitBreaker(name = FLIGHT_OPS_CB, fallbackMethod = "getFlightBookingInfoFallback")
    @Retry(name = FLIGHT_OPS_CB, fallbackMethod = "getFlightBookingInfoFallback")
    @Bulkhead(name = FLIGHT_OPS_CB)
    @RateLimiter(name = FLIGHT_OPS_CB)
    public FlightBookingInfo getFlightBookingInfo(String flightNumber) {
        String url = flightOpsBaseUrl + "/api/flights/" + flightNumber.toUpperCase() + "/bookable";
        log.info("Fetching flight info from FLIGHT-OPS-SERVICE: {}", flightNumber);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null) {
            log.warn("Empty response from flight-ops-service for flight: {}", flightNumber);
            throw new RuntimeException("Flight service returned empty response");
        }

        // Parse scheduled departure time if available
        LocalTime scheduledDeparture = parseScheduledDeparture(response.get("scheduledDeparture"));

        return new FlightBookingInfo(
                Boolean.TRUE.equals(response.get("bookable")),
                (String) response.getOrDefault("reason", "Unknown"),
                response.get("availableSeats") != null ? ((Number) response.get("availableSeats")).intValue() : 0,
                (String) response.get("origin"),
                (String) response.get("destination"),
                scheduledDeparture
        );
    }

    /**
     * Fallback method when circuit is open or call fails.
     * Provides degraded but usable response.
     */
    private FlightBookingInfo getFlightBookingInfoFallback(String flightNumber, Throwable t) {
        log.warn("Circuit breaker fallback for flight {}: {}", flightNumber, t.getMessage());

        // Return a cautious response - allow booking but with limited info
        // In production, you might want to fail closed for safety
        return new FlightBookingInfo(
                true,
                "Flight service temporarily unavailable - limited validation",
                100, // Conservative seat estimate
                "IST",
                "---", // Unknown destination
                LocalTime.of(12, 0) // Default noon departure
        );
    }

    private LocalTime parseScheduledDeparture(Object depTime) {
        if (depTime == null) {
            return null;
        }
        try {
            // Try ISO LocalDateTime format first
            LocalDateTime depDateTime = LocalDateTime.parse(depTime.toString());
            return depDateTime.toLocalTime();
        } catch (Exception e) {
            try {
                // Fallback to HH:mm format
                return LocalTime.parse(depTime.toString(), DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception ex) {
                log.debug("Could not parse departure time: {}", depTime);
                return null;
            }
        }
    }

    /**
     * Flight booking information record.
     */
    public record FlightBookingInfo(
            boolean bookable,
            String reason,
            int availableSeats,
            String origin,
            String destination,
            LocalTime scheduledDeparture
    ) {}
}
