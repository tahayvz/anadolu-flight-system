package com.anadoluair.flight.bookingservice.controller;

import com.anadoluair.flight.bookingservice.kafka.DeadLetterQueueHandler;
import com.anadoluair.flight.bookingservice.kafka.DeadLetterQueueHandler.DLQStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Dead Letter Queue (DLQ) Management.
 *
 * Provides operational endpoints for:
 * - Monitoring DLQ statistics
 * - Manual message reprocessing
 * - Counter reset after investigation
 */
@Slf4j
/*
 * DLQ yonetim uclari, DLQ isleyicisiyle ayni kosula baglidir. Isleyici kapaliyken
 * (spring.kafka.consumer.auto-startup=false) bu controller da yuklenmez; aksi halde
 * var olmayan bir bean'e bagimli kalir ve uygulama hic ayaga kalkmaz.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.kafka.consumer.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
@Tag(name = "DLQ Management", description = "Dead Letter Queue monitoring and management")
public class DLQController {

    private final DeadLetterQueueHandler dlqHandler;

    /**
     * Get DLQ statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get DLQ statistics", description = "Returns counts and timestamps of failed messages")
    public ResponseEntity<DLQStats> getStats() {
        DLQStats stats = dlqHandler.getStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get DLQ health status.
     */
    @GetMapping("/health")
    @Operation(summary = "Check DLQ health", description = "Returns health status based on error counts")
    public ResponseEntity<Map<String, Object>> getHealth() {
        DLQStats stats = dlqHandler.getStats();
        long totalErrors = stats.bookingEventsCount() + stats.flightEventsCount();

        String status;
        if (totalErrors == 0) {
            status = "HEALTHY";
        } else if (totalErrors < 10) {
            status = "WARNING";
        } else {
            status = "CRITICAL";
        }

        return ResponseEntity.ok(Map.of(
                "status", status,
                "totalErrors", totalErrors,
                "bookingErrors", stats.bookingEventsCount(),
                "flightErrors", stats.flightEventsCount(),
                "lastBookingError", stats.lastBookingError() != null ? stats.lastBookingError().toString() : "none",
                "lastFlightError", stats.lastFlightError() != null ? stats.lastFlightError().toString() : "none"
        ));
    }

    /**
     * Republish a message from DLQ to original topic.
     */
    @PostMapping("/republish")
    @Operation(summary = "Republish message", description = "Manually republish a failed message to its original topic")
    public ResponseEntity<Map<String, String>> republishMessage(
            @RequestParam String topic,
            @RequestParam String key,
            @RequestBody String value
    ) {
        log.info("Manual republish request: topic={}, key={}", topic, key);

        try {
            dlqHandler.republishMessage(topic, key, value);
            return ResponseEntity.ok(Map.of(
                    "status", "SUBMITTED",
                    "message", "Message submitted for republishing to topic: " + topic
            ));
        } catch (Exception e) {
            log.error("Failed to republish message: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Reset DLQ counters after investigation.
     */
    @PostMapping("/reset")
    @Operation(summary = "Reset counters", description = "Reset DLQ error counters after investigation")
    public ResponseEntity<Map<String, String>> resetCounters() {
        log.info("Resetting DLQ counters");
        dlqHandler.resetCounters();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "DLQ counters have been reset"
        ));
    }
}
