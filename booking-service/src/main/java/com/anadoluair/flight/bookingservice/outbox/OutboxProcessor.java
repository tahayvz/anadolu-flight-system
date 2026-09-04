package com.anadoluair.flight.bookingservice.outbox;

import com.anadoluair.flight.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Outbox Processor - Polls the outbox table and publishes events to Kafka.
 *
 * This implements the "Polling Publisher" pattern:
 * - Runs on a schedule (every 1 second)
 * - Reads pending events from outbox
 * - Publishes to Kafka
 * - Marks events as completed
 *
 * Alternative: CDC (Change Data Capture) with Debezium for real-time processing
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    // Saga topics that need special handling
    private static final Set<String> SAGA_TOPICS = Set.of(
            KafkaTopics.SAGA_COMMANDS,
            KafkaTopics.SAGA_REPLIES,
            KafkaTopics.SAGA_COMPENSATION
    );

    /**
     * Process pending outbox events every second.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        int processed = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            if (processed >= BATCH_SIZE) {
                break;
            }

            try {
                processEvent(event);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getEventId(), e.getMessage());
                event.markFailed(e.getMessage());
                failed++;
            }
        }

        if (processed > 0 || failed > 0) {
            log.info("Outbox processor: processed={}, failed={}", processed, failed);
        }
    }

    /**
     * Recover stuck events (in PROCESSING state too long).
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    @Transactional
    public void recoverStuckEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<OutboxEvent> stuckEvents = outboxRepository.findStuckEvents(threshold);

        for (OutboxEvent event : stuckEvents) {
            log.warn("Recovering stuck event: {}", event.getEventId());
            event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        }

        if (!stuckEvents.isEmpty()) {
            log.info("Recovered {} stuck events", stuckEvents.size());
        }
    }

    /**
     * Cleanup old completed events (older than 7 days).
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
    @Transactional
    public void cleanupCompletedEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = outboxRepository.deleteCompletedEventsBefore(threshold);
        log.info("Cleaned up {} completed outbox events", deleted);
    }

    private void processEvent(OutboxEvent event) {
        event.markProcessing();
        outboxRepository.save(event);

        try {
            // Determine the key based on topic type
            // For Saga topics, aggregateId is sagaId
            // For Booking topics, aggregateId is PNR
            String key = event.getAggregateId();

            // Log more details for saga events
            if (SAGA_TOPICS.contains(event.getTopic())) {
                log.debug("Processing saga outbox event: type={}, sagaId={}, topic={}",
                        event.getEventType(), key, event.getTopic());
            }

            // Publish to Kafka
            kafkaTemplate.send(event.getTopic(), key, event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka send failed for event {}: {}", event.getEventId(), ex.getMessage());
                            // Will be retried on next poll
                        }
                    })
                    .get(); // Block until send completes

            // Mark as completed
            event.markCompleted();
            outboxRepository.save(event);

            log.debug("Published outbox event: {} to topic {} with key {}",
                    event.getEventId(), event.getTopic(), key);

        } catch (Exception e) {
            event.markFailed(e.getMessage());
            outboxRepository.save(event);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    /**
     * Get outbox statistics for monitoring.
     */
    public OutboxStats getStats() {
        return new OutboxStats(
                outboxRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING),
                outboxRepository.countByStatus(OutboxEvent.OutboxStatus.PROCESSING),
                outboxRepository.countByStatus(OutboxEvent.OutboxStatus.COMPLETED),
                outboxRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED)
        );
    }

    // ==================== Manual Trigger Methods ====================

    /**
     * Manually trigger processing of pending events.
     * Useful for operational needs when immediate processing is required.
     *
     * @return number of events processed
     */
    public int triggerManualProcessing() {
        log.info("Manual outbox processing triggered");
        int processed = 0;
        int maxIterations = 10; // Safety limit

        for (int i = 0; i < maxIterations; i++) {
            List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents();
            if (pendingEvents.isEmpty()) {
                break;
            }

            for (OutboxEvent event : pendingEvents) {
                try {
                    processEvent(event);
                    processed++;
                } catch (Exception e) {
                    log.error("Manual processing failed for event {}: {}", event.getEventId(), e.getMessage());
                }
            }
        }

        log.info("Manual processing completed: {} events processed", processed);
        return processed;
    }

    /**
     * Manually trigger recovery of stuck events.
     *
     * @return number of events recovered
     */
    public int triggerManualRecovery() {
        log.info("Manual stuck event recovery triggered");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<OutboxEvent> stuckEvents = outboxRepository.findStuckEvents(threshold);

        for (OutboxEvent event : stuckEvents) {
            log.info("Recovering stuck event: {}", event.getEventId());
            event.setStatus(OutboxEvent.OutboxStatus.PENDING);
            outboxRepository.save(event);
        }

        log.info("Manual recovery completed: {} events recovered", stuckEvents.size());
        return stuckEvents.size();
    }

    /**
     * Manually trigger cleanup of old events.
     *
     * @param daysOld events older than this many days will be deleted
     * @return number of events deleted
     */
    public int triggerManualCleanup(int daysOld) {
        log.info("Manual cleanup triggered for events older than {} days", daysOld);
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        int deleted = outboxRepository.deleteCompletedEventsBefore(threshold);
        log.info("Manual cleanup completed: {} events deleted", deleted);
        return deleted;
    }

    /**
     * Retry failed events by resetting their status to pending.
     *
     * @return number of events reset for retry
     */
    public int retryFailedEvents() {
        log.info("Retrying failed events");
        List<OutboxEvent> failedEvents = outboxRepository.findByStatus(OutboxEvent.OutboxStatus.FAILED);

        for (OutboxEvent event : failedEvents) {
            log.info("Resetting failed event for retry: {}", event.getEventId());
            event.setStatus(OutboxEvent.OutboxStatus.PENDING);
            event.setErrorMessage(null);
            outboxRepository.save(event);
        }

        log.info("Reset {} failed events for retry", failedEvents.size());
        return failedEvents.size();
    }

    public record OutboxStats(long pending, long processing, long completed, long failed) {}
}
