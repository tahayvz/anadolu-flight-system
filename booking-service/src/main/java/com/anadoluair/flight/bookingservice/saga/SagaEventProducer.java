package com.anadoluair.flight.bookingservice.saga;

import com.anadoluair.flight.bookingservice.outbox.OutboxService;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.events.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka producer for Saga events.
 *
 * Supports two modes:
 * 1. Direct send - immediate Kafka publish (default for replies)
 * 2. Outbox pattern - persists event first, then async publish (for commands)
 *
 * Uses sagaId as the partition key to ensure ordering.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxService outboxService;

    @Value("${saga.outbox.enabled:true}")
    private boolean outboxEnabled;

    /**
     * Send a saga command to be processed by another service.
     * Uses Outbox pattern for guaranteed delivery.
     */
    public void sendCommand(SagaEvent event) {
        prepareEvent(event);

        log.info("Sending saga command: sagaId={}, type={}, step={}",
                event.getSagaId(), event.getEventType(), event.getCurrentStep());

        if (outboxEnabled) {
            // Use Outbox pattern - event is persisted first
            outboxService.saveSagaCommand(event);
            log.debug("Saga command saved to outbox: sagaId={}", event.getSagaId());
        } else {
            // Direct send
            sendDirect(KafkaTopics.SAGA_COMMANDS, event);
        }
    }

    /**
     * Send a saga reply after processing a command.
     * Replies use direct send for low latency.
     */
    public void sendReply(SagaEvent event) {
        prepareEvent(event);

        log.info("Sending saga reply: sagaId={}, type={}", event.getSagaId(), event.getEventType());

        if (outboxEnabled) {
            // Use Outbox for replies too - ensures consistency
            outboxService.saveSagaReply(event);
            log.debug("Saga reply saved to outbox: sagaId={}", event.getSagaId());
        } else {
            sendDirect(KafkaTopics.SAGA_REPLIES, event);
        }
    }

    /**
     * Send a compensation command for rollback.
     * Compensation commands are critical - always use Outbox.
     */
    public void sendCompensationCommand(SagaEvent event) {
        prepareEvent(event);

        log.warn("Sending compensation command: sagaId={}, type={}", event.getSagaId(), event.getEventType());

        if (outboxEnabled) {
            // Always use Outbox for compensation - cannot afford to lose these
            outboxService.saveSagaCompensation(event);
            log.debug("Saga compensation saved to outbox: sagaId={}", event.getSagaId());
        } else {
            sendDirect(KafkaTopics.SAGA_COMPENSATION, event);
        }
    }

    /**
     * Direct send to Kafka (bypasses Outbox).
     * Used when Outbox is disabled or for time-critical messages.
     */
    private void sendDirect(String topic, SagaEvent event) {
        kafkaTemplate.send(topic, event.getSagaId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send saga event: sagaId={}, topic={}, error={}",
                                event.getSagaId(), topic, ex.getMessage());
                    } else {
                        log.debug("Saga event sent directly: topic={}, partition={}, offset={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Prepare event with defaults.
     */
    private void prepareEvent(SagaEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        event.setSourceService("booking-service");
    }

    /**
     * Create a command event from saga state.
     */
    public SagaEvent createCommandEvent(BookingSagaState state, SagaEvent.SagaEventType eventType) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(state.getSagaId())
                .eventType(eventType)
                .currentStep(mapToSagaStepType(state.getCurrentStep()))
                .timestamp(LocalDateTime.now())
                .bookingReference(state.getBookingReference())
                .flightNumber(state.getFlightNumber())
                .flightDate(state.getFlightDate())
                .passengerCount(state.getPassengerCount())
                .totalAmount(state.getTotalAmount())
                .currency(state.getCurrency())
                .seatReservationId(state.getSeatReservationId())
                .paymentId(state.getPaymentId())
                .paymentMethod(state.getPaymentMethod())
                .correlationId(state.getCorrelationId())
                .sourceService("booking-service")
                .build();
    }

    /**
     * Create a success reply event.
     */
    public SagaEvent createSuccessReply(BookingSagaState state, SagaEvent.SagaEventType successType) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(state.getSagaId())
                .eventType(successType)
                .currentStep(mapToSagaStepType(state.getCurrentStep()))
                .timestamp(LocalDateTime.now())
                .bookingReference(state.getBookingReference())
                .flightNumber(state.getFlightNumber())
                .flightDate(state.getFlightDate())
                .seatReservationId(state.getSeatReservationId())
                .paymentId(state.getPaymentId())
                .totalAmount(state.getTotalAmount())
                .correlationId(state.getCorrelationId())
                .sourceService("booking-service")
                .build();
    }

    /**
     * Create a failure reply event.
     */
    public SagaEvent createFailureReply(BookingSagaState state, SagaEvent.SagaEventType failureType, String errorMessage) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(state.getSagaId())
                .eventType(failureType)
                .currentStep(mapToSagaStepType(state.getCurrentStep()))
                .timestamp(LocalDateTime.now())
                .bookingReference(state.getBookingReference())
                .flightNumber(state.getFlightNumber())
                .errorMessage(errorMessage)
                .failedStep(state.getCurrentStep() != null ? state.getCurrentStep().name() : null)
                .retryCount(state.getRetryCount())
                .correlationId(state.getCorrelationId())
                .sourceService("booking-service")
                .build();
    }

    private SagaEvent.SagaStepType mapToSagaStepType(BookingSagaState.SagaStep step) {
        if (step == null) return null;
        return SagaEvent.SagaStepType.valueOf(step.name());
    }

    /**
     * Check if Outbox is enabled.
     */
    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }
}
