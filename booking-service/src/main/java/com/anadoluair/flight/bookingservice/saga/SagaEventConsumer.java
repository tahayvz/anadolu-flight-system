package com.anadoluair.flight.bookingservice.saga;

import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.events.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for Saga events.
 *
 * Listens to:
 * - SAGA_REPLIES: Responses from other services after executing commands
 * - SAGA_COMPENSATION: Compensation commands for rollback
 *
 * This enables async, event-driven saga orchestration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.kafka.consumer.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class SagaEventConsumer {

    private final BookingSagaOrchestrator sagaOrchestrator;
    private final SagaStateRepository sagaStateRepository;

    /**
     * Listen for saga reply events from other services.
     * These are responses to commands we sent.
     */
    @KafkaListener(
            topics = KafkaTopics.SAGA_REPLIES,
            groupId = "booking-saga-orchestrator",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSagaReply(ConsumerRecord<String, SagaEvent> record, Acknowledgment ack) {
        SagaEvent event = record.value();
        log.info("Received saga reply: sagaId={}, type={}, from={}",
                event.getSagaId(), event.getEventType(), event.getSourceService());

        try {
            processReplyEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process saga reply: sagaId={}, error={}",
                    event.getSagaId(), e.getMessage(), e);
            // Don't ack - will be retried or sent to DLQ
        }
    }

    /**
     * Listen for compensation commands.
     * These trigger rollback actions.
     */
    @KafkaListener(
            topics = KafkaTopics.SAGA_COMPENSATION,
            groupId = "booking-saga-compensator",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleCompensationCommand(ConsumerRecord<String, SagaEvent> record, Acknowledgment ack) {
        SagaEvent event = record.value();
        log.warn("Received compensation command: sagaId={}, type={}",
                event.getSagaId(), event.getEventType());

        try {
            processCompensationEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process compensation: sagaId={}, error={}",
                    event.getSagaId(), e.getMessage(), e);
        }
    }

    /**
     * Process reply events and advance the saga.
     */
    private void processReplyEvent(SagaEvent event) {
        var stateOpt = sagaStateRepository.findById(event.getSagaId());
        if (stateOpt.isEmpty()) {
            log.warn("Saga not found for reply: {}", event.getSagaId());
            return;
        }

        BookingSagaState state = stateOpt.get();

        switch (event.getEventType()) {
            // Success replies - advance to next step
            case VALIDATE_FLIGHT_SUCCESS:
                log.info("Flight validation successful for saga: {}", state.getSagaId());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.VALIDATE_FLIGHT);
                break;

            case RESERVE_SEATS_SUCCESS:
                log.info("Seat reservation successful for saga: {}", state.getSagaId());
                state.setSeatReservationId(event.getSeatReservationId());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.RESERVE_SEATS);
                break;

            case VALIDATE_PASSENGERS_SUCCESS:
                log.info("Passenger validation successful for saga: {}", state.getSagaId());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.VALIDATE_PASSENGERS);
                break;

            case CALCULATE_PRICE_SUCCESS:
                log.info("Price calculation successful for saga: {}", state.getSagaId());
                state.setTotalAmount(event.getTotalAmount());
                state.setCurrency(event.getCurrency());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.CALCULATE_PRICE);
                break;

            case PROCESS_PAYMENT_SUCCESS:
                log.info("Payment processed successfully for saga: {}", state.getSagaId());
                state.setPaymentId(event.getPaymentId());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.PROCESS_PAYMENT);
                break;

            case CREATE_BOOKING_SUCCESS:
                log.info("Booking created successfully for saga: {}", state.getSagaId());
                state.setBookingReference(event.getBookingReference());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.CREATE_BOOKING);
                break;

            case SEND_CONFIRMATION_SUCCESS:
                log.info("Confirmation sent successfully for saga: {}", state.getSagaId());
                sagaOrchestrator.handleStepSuccess(state, BookingSagaState.SagaStep.SEND_CONFIRMATION);
                break;

            // Failure replies - trigger compensation
            case VALIDATE_FLIGHT_FAILED:
            case RESERVE_SEATS_FAILED:
            case VALIDATE_PASSENGERS_FAILED:
            case CALCULATE_PRICE_FAILED:
            case PROCESS_PAYMENT_FAILED:
            case CREATE_BOOKING_FAILED:
            case SEND_CONFIRMATION_FAILED:
                log.error("Step failed for saga: {}, error: {}", state.getSagaId(), event.getErrorMessage());
                sagaOrchestrator.handleStepFailure(state, event.getErrorMessage());
                break;

            // Compensation results
            case RELEASE_SEATS_SUCCESS:
                log.info("Seats released for saga: {}", state.getSagaId());
                sagaOrchestrator.handleCompensationSuccess(state, BookingSagaState.SagaStep.RELEASE_SEATS);
                break;

            case REFUND_PAYMENT_SUCCESS:
                log.info("Payment refunded for saga: {}", state.getSagaId());
                sagaOrchestrator.handleCompensationSuccess(state, BookingSagaState.SagaStep.REFUND_PAYMENT);
                break;

            case CANCEL_BOOKING_SUCCESS:
                log.info("Booking cancelled for saga: {}", state.getSagaId());
                sagaOrchestrator.handleCompensationSuccess(state, BookingSagaState.SagaStep.CANCEL_BOOKING);
                break;

            case RELEASE_SEATS_FAILED:
            case REFUND_PAYMENT_FAILED:
            case CANCEL_BOOKING_FAILED:
                log.error("Compensation failed for saga: {}, error: {}", state.getSagaId(), event.getErrorMessage());
                sagaOrchestrator.handleCompensationFailure(state, event.getErrorMessage());
                break;

            case COMPENSATION_COMPLETED:
                // Don't call completeCompensation again - it was already called when sending this event
                // This event is for external consumers/monitoring, just log and ignore
                if (state.getStatus() == BookingSagaState.SagaStatus.ROLLED_BACK) {
                    log.info("Received COMPENSATION_COMPLETED event for already rolled back saga: {}", state.getSagaId());
                } else {
                    log.info("All compensations completed for saga: {}", state.getSagaId());
                    // Only update state, don't send another event
                    state.setStatus(BookingSagaState.SagaStatus.ROLLED_BACK);
                    state.setCompletedAt(java.time.LocalDateTime.now());
                    sagaStateRepository.save(state);
                }
                break;

            case SAGA_COMPLETED:
                // This is a terminal event - just log, no action needed
                log.info("Saga completed notification received for saga: {}", state.getSagaId());
                break;

            default:
                log.warn("Unknown event type received: {}", event.getEventType());
        }
    }

    /**
     * Process compensation events and execute rollback.
     */
    private void processCompensationEvent(SagaEvent event) {
        var stateOpt = sagaStateRepository.findById(event.getSagaId());
        if (stateOpt.isEmpty()) {
            log.warn("Saga not found for compensation: {}", event.getSagaId());
            return;
        }

        BookingSagaState state = stateOpt.get();

        switch (event.getEventType()) {
            case COMPENSATE_STARTED:
                log.warn("Starting compensation for saga: {}", state.getSagaId());
                sagaOrchestrator.startCompensation(state);
                break;

            case RELEASE_SEATS_CMD:
                log.info("Executing seat release for saga: {}", state.getSagaId());
                sagaOrchestrator.executeReleaseSeats(state);
                break;

            case REFUND_PAYMENT_CMD:
                log.info("Executing payment refund for saga: {}", state.getSagaId());
                sagaOrchestrator.executeRefundPayment(state);
                break;

            case CANCEL_BOOKING_CMD:
                log.info("Executing booking cancellation for saga: {}", state.getSagaId());
                sagaOrchestrator.executeCancelBooking(state);
                break;

            default:
                log.warn("Unknown compensation event type: {}", event.getEventType());
        }
    }
}
