package com.anadoluair.flight.flightopsservice.controller;

import com.anadoluair.flight.events.FlightState;
import com.anadoluair.flight.flightopsservice.dto.FlightStatusDTO;
import com.anadoluair.flight.flightopsservice.repository.FlightStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for flight operations.
 * Provides HTTP endpoints alongside WebSocket for flexibility.
 */
@Slf4j
@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FlightController {

    private final FlightStateRepository flightStateRepository;

    /**
     * Get all flight statuses (for initial page load).
     */
    @GetMapping
    public ResponseEntity<List<FlightStatusDTO>> getAllFlights() {
        log.info("GET /api/flights - Retrieving all flight statuses");

        List<FlightStatusDTO> flights = flightStateRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(flights);
    }

    /**
     * Get status for a specific flight.
     */
    @GetMapping("/{flightNumber}")
    public ResponseEntity<FlightStatusDTO> getFlightStatus(@PathVariable String flightNumber) {
        log.info("GET /api/flights/{} - Retrieving flight status", flightNumber);

        return flightStateRepository.findByFlightNumber(flightNumber.toUpperCase())
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if a flight is bookable.
     * Returns booking eligibility, available seats, and reason.
     */
    @GetMapping("/{flightNumber}/bookable")
    public ResponseEntity<Map<String, Object>> isFlightBookable(@PathVariable String flightNumber) {
        log.info("GET /api/flights/{}/bookable - Checking if flight is bookable", flightNumber);

        return flightStateRepository.findByFlightNumber(flightNumber.toUpperCase())
                .map(flight -> {
                    boolean bookable = flight.isBookable();
                    int availableSeats = flight.getAvailableSeats();

                    String reason;
                    if (!bookable) {
                        reason = "Flight status " + flight.getStatus() + " does not allow booking";
                    } else if (availableSeats <= 0) {
                        reason = "No seats available";
                    } else {
                        reason = "Flight available for booking";
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("flightNumber", flight.getFlightNumber());
                    response.put("bookable", bookable && availableSeats > 0);
                    response.put("status", flight.getStatus().name());
                    response.put("availableSeats", availableSeats);
                    response.put("origin", flight.getOrigin());
                    response.put("destination", flight.getDestination());
                    response.put("scheduledDeparture", flight.getScheduledDeparture().toString());
                    response.put("reason", reason);

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> notFound = new HashMap<>();
                    notFound.put("flightNumber", flightNumber.toUpperCase());
                    notFound.put("bookable", false);
                    notFound.put("availableSeats", 0);
                    notFound.put("reason", "Flight not found");
                    return ResponseEntity.ok(notFound);
                });
    }

    /**
     * Get all bookable flights.
     */
    @GetMapping("/bookable")
    public ResponseEntity<List<FlightStatusDTO>> getBookableFlights() {
        log.info("GET /api/flights/bookable - Retrieving bookable flights");

        List<FlightStatusDTO> flights = flightStateRepository.findBookableFlights()
                .stream()
                .filter(f -> f.getAvailableSeats() > 0)
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(flights);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "flight-ops-service",
                "flightCount", flightStateRepository.count(),
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * Map FlightState to FlightStatusDTO.
     */
    private FlightStatusDTO mapToDTO(FlightState flight) {
        return FlightStatusDTO.builder()
                .flightNumber(flight.getFlightNumber())
                .airline(flight.getAirline())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .status(flight.getStatus().name())
                .gate(flight.getGate())
                .terminal(flight.getTerminal())
                .scheduledDeparture(flight.getScheduledDeparture())
                .estimatedDeparture(flight.getEstimatedDeparture())
                .scheduledArrival(flight.getScheduledArrival())
                .estimatedArrival(flight.getEstimatedArrival())
                .aircraft(flight.getAircraft())
                .lastUpdated(flight.getLastUpdated())
                .build();
    }
}
