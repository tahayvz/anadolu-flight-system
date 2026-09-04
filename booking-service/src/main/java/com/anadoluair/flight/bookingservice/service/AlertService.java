package com.anadoluair.flight.bookingservice.service;

import com.anadoluair.flight.bookingservice.saga.BookingSagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Alert Service for critical system events.
 *
 * Provides alerting capabilities for:
 * - Saga compensation failures
 * - High DLQ error rates
 * - System health issues
 *
 * In production, this would integrate with:
 * - PagerDuty
 * - Slack
 * - Email
 * - SNS/SQS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    @Value("${alerts.enabled:true}")
    private boolean alertsEnabled;

    @Value("${alerts.throttle-minutes:5}")
    private int throttleMinutes;

    // Track last alert times to prevent alert storms
    private final Map<String, LocalDateTime> lastAlertTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> alertCounts = new ConcurrentHashMap<>();

    /**
     * Alert when saga compensation fails.
     * This is a critical alert requiring immediate attention.
     */
    public void sendSagaCompensationFailedAlert(BookingSagaState state) {
        String alertKey = "saga-compensation-failed";

        if (!shouldSendAlert(alertKey)) {
            log.debug("Alert throttled: {}", alertKey);
            return;
        }

        String message = String.format(
                "CRITICAL: Saga compensation failed!\n" +
                        "Saga ID: %s\n" +
                        "Flight: %s (%s)\n" +
                        "Failed Step: %s\n" +
                        "Error: %s\n" +
                        "PNR: %s\n" +
                        "Contact: %s\n" +
                        "Manual intervention required!",
                state.getSagaId(),
                state.getFlightNumber(),
                state.getFlightDate(),
                state.getFailedStep(),
                state.getErrorMessage(),
                state.getBookingReference(),
                state.getContactEmail()
        );

        sendAlert(AlertLevel.CRITICAL, "Saga Compensation Failed", message, state.getSagaId());
        recordAlertSent(alertKey);
    }

    /**
     * Alert when DLQ error count is high.
     */
    public void sendHighDLQAlertAlert(String source, long count) {
        String alertKey = "high-dlq-" + source;

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        String message = String.format(
                "WARNING: High DLQ message count detected!\n" +
                        "Source: %s\n" +
                        "Count: %d messages\n" +
                        "Please investigate failed message processing.",
                source, count
        );

        sendAlert(AlertLevel.WARNING, "High DLQ Count", message, source);
        recordAlertSent(alertKey);
    }

    /**
     * Alert when saga times out.
     */
    public void sendSagaTimeoutAlert(BookingSagaState state) {
        String alertKey = "saga-timeout-" + state.getSagaId();

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        String message = String.format(
                "WARNING: Saga timed out!\n" +
                        "Saga ID: %s\n" +
                        "Flight: %s\n" +
                        "Stuck Step: %s\n" +
                        "Started At: %s\n" +
                        "Compensation will be initiated.",
                state.getSagaId(),
                state.getFlightNumber(),
                state.getCurrentStep(),
                state.getStartedAt()
        );

        sendAlert(AlertLevel.WARNING, "Saga Timeout", message, state.getSagaId());
        recordAlertSent(alertKey);
    }

    /**
     * Alert for outbox processing issues.
     */
    public void sendOutboxProcessingAlert(long failedCount, long stuckCount) {
        String alertKey = "outbox-processing";

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        String message = String.format(
                "WARNING: Outbox processing issues detected!\n" +
                        "Failed Events: %d\n" +
                        "Stuck Events: %d\n" +
                        "Please check Kafka connectivity and outbox processor health.",
                failedCount, stuckCount
        );

        sendAlert(AlertLevel.WARNING, "Outbox Processing Issue", message, "outbox");
        recordAlertSent(alertKey);
    }

    /**
     * Send a generic alert.
     */
    public void sendAlert(AlertLevel level, String title, String message, String correlationId) {
        if (!alertsEnabled) {
            log.debug("Alerts disabled. Would send: [{}] {} - {}", level, title, correlationId);
            return;
        }

        // Log the alert (always)
        switch (level) {
            case CRITICAL -> log.error("ALERT [CRITICAL] {}: {} (correlationId={})", title, message, correlationId);
            case WARNING -> log.warn("ALERT [WARNING] {}: {} (correlationId={})", title, message, correlationId);
            case INFO -> log.info("ALERT [INFO] {}: {} (correlationId={})", title, message, correlationId);
        }

        // In production, integrate with external alerting systems:
        // sendToPagerDuty(level, title, message, correlationId);
        // sendToSlack(level, title, message, correlationId);
        // sendEmail(level, title, message, correlationId);

        // Track alert count
        alertCounts.computeIfAbsent(level.name(), k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Check if we should send an alert (respecting throttle).
     */
    private boolean shouldSendAlert(String alertKey) {
        LocalDateTime lastAlert = lastAlertTimes.get(alertKey);

        if (lastAlert == null) {
            return true;
        }

        return lastAlert.plusMinutes(throttleMinutes).isBefore(LocalDateTime.now());
    }

    /**
     * Record that an alert was sent.
     */
    private void recordAlertSent(String alertKey) {
        lastAlertTimes.put(alertKey, LocalDateTime.now());
    }

    /**
     * Get alert statistics.
     */
    public AlertStats getStats() {
        return new AlertStats(
                alertCounts.getOrDefault("CRITICAL", new AtomicLong(0)).get(),
                alertCounts.getOrDefault("WARNING", new AtomicLong(0)).get(),
                alertCounts.getOrDefault("INFO", new AtomicLong(0)).get(),
                alertsEnabled,
                throttleMinutes
        );
    }

    /**
     * Reset alert counts (after review).
     */
    public void resetAlertCounts() {
        alertCounts.clear();
        log.info("Alert counts reset");
    }

    /**
     * Clear throttle cache (for testing or emergency).
     */
    public void clearThrottleCache() {
        lastAlertTimes.clear();
        log.info("Alert throttle cache cleared");
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        CRITICAL
    }

    public record AlertStats(
            long criticalCount,
            long warningCount,
            long infoCount,
            boolean alertsEnabled,
            int throttleMinutes
    ) {}
}
