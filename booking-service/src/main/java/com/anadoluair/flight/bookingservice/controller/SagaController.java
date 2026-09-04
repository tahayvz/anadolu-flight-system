package com.anadoluair.flight.bookingservice.controller;

import com.anadoluair.flight.bookingservice.outbox.OutboxProcessor;
import com.anadoluair.flight.bookingservice.outbox.OutboxService;
import com.anadoluair.flight.bookingservice.saga.BookingSagaOrchestrator;
import com.anadoluair.flight.bookingservice.saga.BookingSagaState;
import com.anadoluair.flight.bookingservice.saga.SagaEventProducer;
import com.anadoluair.flight.bookingservice.saga.SagaStateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Saga monitoring and management.
 *
 * Provides endpoints to:
 * - View saga states
 * - Monitor active/failed sagas
 * - Manually trigger compensation
 * - Get saga statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/sagas")
@RequiredArgsConstructor
@Tag(name = "Saga Management", description = "Endpoints for monitoring and managing booking sagas")
public class SagaController {

    private final BookingSagaOrchestrator sagaOrchestrator;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxProcessor outboxProcessor;
    private final OutboxService outboxService;
    private final SagaEventProducer sagaEventProducer;

    /**
     * Get saga state by ID.
     */
    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga state", description = "Retrieve the current state of a saga by its ID")
    public ResponseEntity<BookingSagaState> getSagaState(@PathVariable String sagaId) {
        return sagaOrchestrator.getSagaState(sagaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get saga state by PNR (booking reference).
     */
    @GetMapping("/by-pnr/{pnr}")
    @Operation(summary = "Get saga by PNR", description = "Retrieve saga state using the booking reference (PNR)")
    public ResponseEntity<BookingSagaState> getSagaByPNR(@PathVariable String pnr) {
        return sagaOrchestrator.getSagaByPNR(pnr)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active (in-progress) sagas.
     */
    @GetMapping("/active")
    @Operation(summary = "Get active sagas", description = "List all sagas currently in progress")
    public ResponseEntity<List<BookingSagaState>> getActiveSagas() {
        List<BookingSagaState> activeSagas = sagaOrchestrator.getActiveSagas();
        return ResponseEntity.ok(activeSagas);
    }

    /**
     * Get sagas for a specific flight.
     */
    @GetMapping("/by-flight")
    @Operation(summary = "Get sagas by flight", description = "List all sagas for a specific flight")
    public ResponseEntity<List<BookingSagaState>> getSagasByFlight(
            @RequestParam String flightNumber,
            @RequestParam String flightDate) {
        List<BookingSagaState> sagas = sagaStateRepository.findByFlight(flightNumber, flightDate);
        return ResponseEntity.ok(sagas);
    }

    /**
     * Get sagas currently in compensation (rollback) state.
     */
    @GetMapping("/compensating")
    @Operation(summary = "Get compensating sagas", description = "List all sagas currently undergoing compensation/rollback")
    public ResponseEntity<List<BookingSagaState>> getCompensatingSagas() {
        List<BookingSagaState> compensatingSagas = sagaStateRepository.findCompensatingSagas();
        return ResponseEntity.ok(compensatingSagas);
    }

    /**
     * Get timed out sagas that need attention.
     */
    @GetMapping("/timed-out")
    @Operation(summary = "Get timed out sagas", description = "List sagas that have timed out and need recovery")
    public ResponseEntity<List<BookingSagaState>> getTimedOutSagas() {
        List<BookingSagaState> timedOutSagas = sagaStateRepository.findTimedOutSagas();
        return ResponseEntity.ok(timedOutSagas);
    }

    /**
     * Get saga statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get saga statistics", description = "Get statistics about saga states")
    public ResponseEntity<Map<String, Object>> getSagaStats() {
        SagaStateRepository.SagaStats stats = sagaOrchestrator.getStats();

        Map<String, Object> response = new HashMap<>();
        response.put("active", stats.active());
        response.put("failed", stats.failed());
        response.put("compensating", stats.compensating());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger compensation for a saga.
     * Use this for sagas that are stuck or need manual intervention.
     */
    @PostMapping("/{sagaId}/compensate")
    @Operation(summary = "Trigger compensation", description = "Manually trigger compensation/rollback for a saga")
    public ResponseEntity<BookingSagaState> triggerCompensation(@PathVariable String sagaId) {
        var stateOpt = sagaOrchestrator.getSagaState(sagaId);

        if (stateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookingSagaState state = stateOpt.get();

        if (state.getStatus() == BookingSagaState.SagaStatus.COMPLETED ||
            state.getStatus() == BookingSagaState.SagaStatus.ROLLED_BACK) {
            log.warn("Cannot compensate saga {} - already in terminal state: {}", sagaId, state.getStatus());
            return ResponseEntity.badRequest().body(state);
        }

        log.warn("Manually triggering compensation for saga: {}", sagaId);
        sagaOrchestrator.startCompensation(state);

        return sagaOrchestrator.getSagaState(sagaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retry a failed saga step.
     */
    @PostMapping("/{sagaId}/retry")
    @Operation(summary = "Retry saga step", description = "Retry the current step of a failed saga")
    public ResponseEntity<BookingSagaState> retrySaga(@PathVariable String sagaId) {
        var stateOpt = sagaOrchestrator.getSagaState(sagaId);

        if (stateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookingSagaState state = stateOpt.get();

        if (state.getStatus() == BookingSagaState.SagaStatus.COMPLETED ||
            state.getStatus() == BookingSagaState.SagaStatus.ROLLED_BACK) {
            log.warn("Cannot retry saga {} - already in terminal state: {}", sagaId, state.getStatus());
            return ResponseEntity.badRequest().body(state);
        }

        log.info("Manually retrying saga: {} at step: {}", sagaId, state.getCurrentStep());

        // Reset retry count and execute
        state.setRetryCount(0);
        state.setStatus(BookingSagaState.SagaStatus.IN_PROGRESS);
        sagaStateRepository.save(state);
        sagaOrchestrator.executeNextStep(state);

        return sagaOrchestrator.getSagaState(sagaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health check for saga system.
     */
    @GetMapping("/health")
    @Operation(summary = "Saga health check", description = "Check health of saga system")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        SagaStateRepository.SagaStats stats = sagaOrchestrator.getStats();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("activeSagas", stats.active());
        health.put("failedSagas", stats.failed());
        health.put("compensatingSagas", stats.compensating());

        // Set status to DEGRADED if there are too many failed/compensating sagas
        if (stats.failed() > 10 || stats.compensating() > 5) {
            health.put("status", "DEGRADED");
            health.put("warning", "High number of failed or compensating sagas");
        }

        // Add outbox info
        OutboxProcessor.OutboxStats outboxStats = outboxProcessor.getStats();
        health.put("outboxEnabled", sagaEventProducer.isOutboxEnabled());
        health.put("outboxPending", outboxStats.pending());
        health.put("outboxFailed", outboxStats.failed());

        return ResponseEntity.ok(health);
    }

    /**
     * Get outbox statistics.
     */
    @GetMapping("/outbox/stats")
    @Operation(summary = "Get outbox statistics", description = "Get statistics about the outbox for saga events")
    public ResponseEntity<Map<String, Object>> getOutboxStats() {
        OutboxProcessor.OutboxStats stats = outboxProcessor.getStats();
        long pendingSagaEvents = outboxService.getPendingSagaEventCount();

        Map<String, Object> response = new HashMap<>();
        response.put("outboxEnabled", sagaEventProducer.isOutboxEnabled());
        response.put("total", Map.of(
                "pending", stats.pending(),
                "processing", stats.processing(),
                "completed", stats.completed(),
                "failed", stats.failed()
        ));
        response.put("saga", Map.of(
                "pending", pendingSagaEvents
        ));
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    // ==================== Outbox Manual Trigger Endpoints ====================

    /**
     * Manually trigger outbox processing.
     */
    @PostMapping("/outbox/process")
    @Operation(summary = "Trigger outbox processing", description = "Manually process pending outbox events")
    public ResponseEntity<Map<String, Object>> triggerOutboxProcessing() {
        log.info("Manual outbox processing requested");
        int processed = outboxProcessor.triggerManualProcessing();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "processedCount", processed,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Manually recover stuck outbox events.
     */
    @PostMapping("/outbox/recover")
    @Operation(summary = "Recover stuck events", description = "Manually recover outbox events stuck in processing state")
    public ResponseEntity<Map<String, Object>> triggerOutboxRecovery() {
        log.info("Manual outbox recovery requested");
        int recovered = outboxProcessor.triggerManualRecovery();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "recoveredCount", recovered,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Manually cleanup old outbox events.
     */
    @PostMapping("/outbox/cleanup")
    @Operation(summary = "Cleanup old events", description = "Manually delete old completed outbox events")
    public ResponseEntity<Map<String, Object>> triggerOutboxCleanup(
            @RequestParam(defaultValue = "7") int daysOld) {
        log.info("Manual outbox cleanup requested for events older than {} days", daysOld);
        int deleted = outboxProcessor.triggerManualCleanup(daysOld);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deletedCount", deleted,
                "daysOld", daysOld,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Retry failed outbox events.
     */
    @PostMapping("/outbox/retry-failed")
    @Operation(summary = "Retry failed events", description = "Reset failed outbox events for retry")
    public ResponseEntity<Map<String, Object>> retryFailedOutboxEvents() {
        log.info("Retry failed outbox events requested");
        int retryCount = outboxProcessor.retryFailedEvents();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "retryCount", retryCount,
                "timestamp", System.currentTimeMillis()
        ));
    }
}
