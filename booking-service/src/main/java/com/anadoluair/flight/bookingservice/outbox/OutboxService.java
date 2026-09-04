package com.anadoluair.flight.bookingservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.anadoluair.flight.events.BookingEvent;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.events.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing Outbox Events.
 * Saves events to the outbox table within the same transaction as the business operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final String BOOKING_TOPIC = KafkaTopics.BOOKING_EVENTS;
    private static final String AGGREGATE_TYPE_BOOKING = "Booking";
    private static final String AGGREGATE_TYPE_SAGA = "Saga";

    /**
     * Save a booking event to the outbox.
     * Called within the same transaction as the booking operation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveBookingEvent(BookingEvent event) {
        String eventId = event.getEventId();

        // Idempotency check
        if (outboxRepository.existsByEventId(eventId)) {
            log.warn("Event already exists in outbox: {}", eventId);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(AGGREGATE_TYPE_BOOKING)
                    .aggregateId(event.getBookingData().getBookingReference())
                    .eventType(event.getEventType().name())
                    .topic(BOOKING_TOPIC)
                    .payload(payload)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Saved event to outbox: {} for {}", eventId, event.getBookingData().getBookingReference());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    /**
     * Create a generic outbox event.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(String aggregateType, String aggregateId, String eventType, String topic, Object payload) {
        String eventId = UUID.randomUUID().toString();

        if (outboxRepository.existsByEventId(eventId)) {
            log.warn("Event already exists in outbox: {}", eventId);
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(payloadJson)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Saved event to outbox: {} type {} for {}", eventId, eventType, aggregateId);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    // ==================== Saga Event Methods ====================

    /**
     * Save a saga command event to the outbox.
     * This ensures the command is persisted before being sent to Kafka.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSagaCommand(SagaEvent event) {
        saveSagaEvent(event, KafkaTopics.SAGA_COMMANDS);
    }

    /**
     * Save a saga reply event to the outbox.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSagaReply(SagaEvent event) {
        saveSagaEvent(event, KafkaTopics.SAGA_REPLIES);
    }

    /**
     * Save a saga compensation event to the outbox.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSagaCompensation(SagaEvent event) {
        saveSagaEvent(event, KafkaTopics.SAGA_COMPENSATION);
    }

    /**
     * Internal method to save saga events.
     */
    private void saveSagaEvent(SagaEvent event, String topic) {
        String eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();

        // Idempotency check
        if (outboxRepository.existsByEventId(eventId)) {
            log.warn("Saga event already exists in outbox: {}", eventId);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(AGGREGATE_TYPE_SAGA)
                    .aggregateId(event.getSagaId())
                    .eventType(event.getEventType().name())
                    .topic(topic)
                    .payload(payload)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Saved saga event to outbox: {} type {} for saga {}",
                    eventId, event.getEventType(), event.getSagaId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize saga event: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize saga event", e);
        }
    }

    /**
     * Get count of pending saga events.
     */
    public long getPendingSagaEventCount() {
        return outboxRepository.countPendingByAggregateType(AGGREGATE_TYPE_SAGA);
    }
}
