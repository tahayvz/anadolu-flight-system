package com.anadoluair.flight.bookingservice.controller;

import com.anadoluair.flight.bookingservice.simulation.UserSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for User Simulation.
 *
 * Allows starting/stopping simulated user traffic for:
 * - Load testing
 * - Demo purposes
 * - Observability testing (see traces in Zipkin, metrics in Grafana)
 *
 * Usage:
 *   POST /api/simulation/start  - Start simulation
 *   POST /api/simulation/stop   - Stop simulation
 *   GET  /api/simulation/stats  - Get statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Tag(name = "User Simulation", description = "Control simulated user traffic for testing")
public class SimulationController {

    private final UserSimulator userSimulator;

    /**
     * Start user simulation
     */
    @PostMapping("/start")
    @Operation(summary = "Start simulation", description = "Start simulating user traffic (bookings, check-ins, cancellations)")
    public ResponseEntity<Map<String, Object>> startSimulation() {
        if (userSimulator.isRunning()) {
            return ResponseEntity.ok(Map.of(
                    "status", "ALREADY_RUNNING",
                    "message", "Simulation is already running"
            ));
        }

        userSimulator.start();
        log.info("🚀 User simulation started via API");

        return ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "message", "User simulation started. Check /api/simulation/stats for progress.",
                "endpoints", Map.of(
                        "stop", "POST /api/simulation/stop",
                        "stats", "GET /api/simulation/stats"
                )
        ));
    }

    /**
     * Stop user simulation
     */
    @PostMapping("/stop")
    @Operation(summary = "Stop simulation", description = "Stop simulating user traffic")
    public ResponseEntity<Map<String, Object>> stopSimulation() {
        if (!userSimulator.isRunning()) {
            return ResponseEntity.ok(Map.of(
                    "status", "NOT_RUNNING",
                    "message", "Simulation is not running"
            ));
        }

        userSimulator.stop();
        UserSimulator.SimulationStats stats = userSimulator.getStats();
        log.info("🛑 User simulation stopped via API");

        return ResponseEntity.ok(Map.of(
                "status", "STOPPED",
                "message", "User simulation stopped",
                "finalStats", stats
        ));
    }

    /**
     * Get simulation statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Get simulation stats", description = "Get current simulation statistics")
    public ResponseEntity<UserSimulator.SimulationStats> getStats() {
        return ResponseEntity.ok(userSimulator.getStats());
    }

    /**
     * Get simulation status (simple check)
     */
    @GetMapping("/status")
    @Operation(summary = "Get simulation status", description = "Check if simulation is running")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean running = userSimulator.isRunning();

        return ResponseEntity.ok(Map.of(
                "running", running,
                "status", running ? "RUNNING" : "STOPPED",
                "message", running
                        ? "Simulation is active. Use POST /api/simulation/stop to stop."
                        : "Simulation is inactive. Use POST /api/simulation/start to begin."
        ));
    }
}
