package com.anadoluair.flight.bookingservice.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox Event Entity.
 *
 * The Outbox Pattern ensures reliable message publishing:
 * 1. Business transaction and event are saved in the same DB transaction
 * 2. A separate process (OutboxProcessor) reads pending events and publishes them
 * 3. After successful publish, event is marked as processed
 *
 * Benefits:
 * - Guarantees at-least-once delivery
 * - No distributed transaction needed
 * - Events survive service restarts
 * - Idempotent consumers handle duplicates
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique event identifier for idempotency.
     */
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    /**
     * Aggregate type (e.g., "Booking", "Flight").
     */
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    /**
     * Aggregate ID (e.g., PNR, flight number).
     */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /**
     * Event type (e.g., "BOOKING_CREATED", "BOOKING_CANCELLED").
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Kafka topic to publish to.
     */
    @Column(name = "topic", nullable = false)
    private String topic;

    /**
     * JSON serialized event payload.
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Current status of the event.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /**
     * Number of publish attempts.
     */
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    /**
     * Last error message if publish failed.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * When the event was created.
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * When the event was processed.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public enum OutboxStatus {
        PENDING,      // Waiting to be published
        PROCESSING,   // Currently being published
        COMPLETED,    // Successfully published
        FAILED        // Failed after max retries
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = OutboxStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.errorMessage = error;
        if (this.retryCount >= 5) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
        }
    }
}
