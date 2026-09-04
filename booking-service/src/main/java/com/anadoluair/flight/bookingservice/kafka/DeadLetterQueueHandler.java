package com.anadoluair.flight.bookingservice.kafka;

import com.anadoluair.flight.bookingservice.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dead Letter Queue (DLQ) Handler.
 *
 * Handles messages that failed processing after max retries.
 * DLQ provides:
 * - Message preservation (no data loss)
 * - Error analysis capability
 * - Manual reprocessing option
 * - Alerting for operations team
 *
 * Topics:
 * - booking-events.DLT: Failed booking events
 * - flight-events.DLT: Failed flight events
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.kafka.consumer.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class DeadLetterQueueHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AlertService alertService;

    // Statistics for monitoring
    private final Map<String, AtomicLong> dlqCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastErrorTimes = new ConcurrentHashMap<>();

    /**
     * Handle booking events that failed processing.
     */
    @KafkaListener(
            topics = "booking-events.DLT",
            groupId = "booking-service-dlq-group"
    )
    public void handleBookingEventsDLQ(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic
    ) {
        log.error("DLQ received failed booking event: key={}, topic={}, originalTopic={}, error={}",
                record.key(), topic, originalTopic, errorMessage);

        incrementCount("booking-events");
        lastErrorTimes.put("booking-events", LocalDateTime.now());

        // Store for analysis
        storeFailedEvent("booking-events", record, errorMessage);

        // Alert if error rate is high
        checkAndAlert("booking-events");
    }

    /**
     * Handle flight events that failed processing.
     */
    @KafkaListener(
            topics = "flight-events.DLT",
            groupId = "booking-service-dlq-group"
    )
    public void handleFlightEventsDLQ(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage
    ) {
        log.error("DLQ received failed flight event: key={}, error={}", record.key(), errorMessage);

        incrementCount("flight-events");
        lastErrorTimes.put("flight-events", LocalDateTime.now());

        storeFailedEvent("flight-events", record, errorMessage);
        checkAndAlert("flight-events");
    }

    /**
     * Manually republish a message from DLQ to original topic.
     */
    public void republishMessage(String originalTopic, String key, String value) {
        log.info("Republishing message to topic {}: key={}", originalTopic, key);
        kafkaTemplate.send(originalTopic, key, value)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to republish message: {}", ex.getMessage());
                    } else {
                        log.info("Successfully republished message to {}", originalTopic);
                    }
                });
    }

    private void storeFailedEvent(String source, ConsumerRecord<String, String> record, String error) {
        // In production, store to a database for later analysis
        // For now, just log the details
        log.info("Storing failed event for analysis: source={}, key={}, partition={}, offset={}, timestamp={}",
                source, record.key(), record.partition(), record.offset(), record.timestamp());
    }

    private void incrementCount(String source) {
        dlqCounts.computeIfAbsent(source, k -> new AtomicLong(0)).incrementAndGet();
    }

    private void checkAndAlert(String source) {
        long count = dlqCounts.getOrDefault(source, new AtomicLong(0)).get();

        // Alert if more than 10 errors in DLQ
        if (count > 10) {
            log.warn("High DLQ count for {}: {} messages", source, count);
            alertService.sendHighDLQAlertAlert(source, count);
        }
    }

    /**
     * Get DLQ statistics for monitoring endpoint.
     */
    public DLQStats getStats() {
        return new DLQStats(
                dlqCounts.getOrDefault("booking-events", new AtomicLong(0)).get(),
                dlqCounts.getOrDefault("flight-events", new AtomicLong(0)).get(),
                lastErrorTimes.get("booking-events"),
                lastErrorTimes.get("flight-events")
        );
    }

    /**
     * Reset counters (after investigation).
     */
    public void resetCounters() {
        dlqCounts.clear();
        log.info("DLQ counters reset");
    }

    public record DLQStats(
            long bookingEventsCount,
            long flightEventsCount,
            LocalDateTime lastBookingError,
            LocalDateTime lastFlightError
    ) {}
}
