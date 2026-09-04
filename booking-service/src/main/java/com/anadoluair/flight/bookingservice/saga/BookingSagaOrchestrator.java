package com.anadoluair.flight.bookingservice.saga;

import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.saga.BookingSagaState.SagaStatus;
import com.anadoluair.flight.bookingservice.saga.BookingSagaState.SagaStep;
import com.anadoluair.flight.bookingservice.service.AlertService;
import com.anadoluair.flight.events.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saga Orchestrator for Booking Workflow.
 *
 * Implements the Saga Pattern for distributed transactions:
 * - Coordinates multiple steps across services via Kafka
 * - Provides compensating transactions for rollback
 * - Maintains saga state in Redis for persistence and recovery
 *
 * Flow:
 * 1. Booking request comes in → Start saga
 * 2. Send command to Kafka for each step
 * 3. Wait for reply (success/failure)
 * 4. On success → advance to next step
 * 5. On failure → start compensation (rollback)
 *
 * This is the "Orchestration" style saga where a central coordinator
 * directs the workflow via Kafka events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final SagaEventProducer sagaEventProducer;
    private final AlertService alertService;

    /**
     * Start a new booking saga.
     */
    public BookingSagaState startSaga(BookingRequest request) {
        String sagaId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        BookingSagaState state = BookingSagaState.builder()
                .sagaId(sagaId)
                .status(SagaStatus.STARTED)
                .currentStep(SagaStep.VALIDATE_FLIGHT)
                .startedAt(LocalDateTime.now())
                .lastUpdatedAt(LocalDateTime.now())
                .flightNumber(request.getFlightNumber())
                .flightDate(request.getFlightDate().toString())
                .passengerCount(request.getPassengers().size())
                .currency("TRY")
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .correlationId(correlationId)
                .build();

        // Persist to Redis
        sagaStateRepository.save(state);

        log.info("Started booking saga: {} for flight {} with {} passengers",
                sagaId, request.getFlightNumber(), request.getPassengers().size());

        // Start first step via Kafka
        executeNextStep(state);

        return state;
    }

    /**
     * Execute the next step in the saga by sending Kafka command.
     */
    public void executeNextStep(BookingSagaState state) {
        if (state.getStatus() == SagaStatus.COMPENSATING) {
            executeNextCompensation(state);
            return;
        }

        state.setStatus(SagaStatus.IN_PROGRESS);
        state.setLastUpdatedAt(LocalDateTime.now());

        SagaEvent.SagaEventType commandType;

        switch (state.getCurrentStep()) {
            case VALIDATE_FLIGHT:
                commandType = SagaEvent.SagaEventType.VALIDATE_FLIGHT_CMD;
                break;
            case RESERVE_SEATS:
                commandType = SagaEvent.SagaEventType.RESERVE_SEATS_CMD;
                break;
            case VALIDATE_PASSENGERS:
                commandType = SagaEvent.SagaEventType.VALIDATE_PASSENGERS_CMD;
                break;
            case CALCULATE_PRICE:
                commandType = SagaEvent.SagaEventType.CALCULATE_PRICE_CMD;
                break;
            case PROCESS_PAYMENT:
                commandType = SagaEvent.SagaEventType.PROCESS_PAYMENT_CMD;
                break;
            case CREATE_BOOKING:
                commandType = SagaEvent.SagaEventType.CREATE_BOOKING_CMD;
                break;
            case SEND_CONFIRMATION:
                commandType = SagaEvent.SagaEventType.SEND_CONFIRMATION_CMD;
                break;
            case COMPLETE:
                completeSaga(state);
                return;
            default:
                log.error("Unknown saga step: {}", state.getCurrentStep());
                return;
        }

        // Mark as waiting for response
        state.markWaitingForResponse(state.getCurrentStep());
        sagaStateRepository.save(state);

        // Send command via Kafka
        SagaEvent command = sagaEventProducer.createCommandEvent(state, commandType);
        sagaEventProducer.sendCommand(command);

        log.info("Saga {}: Sent command {} for step {}",
                state.getSagaId(), commandType, state.getCurrentStep());
    }

    /**
     * Handle successful step completion (called from Kafka consumer).
     */
    public void handleStepSuccess(BookingSagaState state, SagaStep completedStep) {
        state.markStepCompleted(completedStep);
        state.setStatus(SagaStatus.IN_PROGRESS);
        state.setRetryCount(0); // Reset retry count on success

        // Move to next step
        SagaStep nextStep = getNextStep(completedStep);
        state.setCurrentStep(nextStep);

        sagaStateRepository.save(state);

        log.info("Saga {}: Step {} completed, moving to {}",
                state.getSagaId(), completedStep, nextStep);

        // Execute next step
        executeNextStep(state);
    }

    /**
     * Handle step failure (called from Kafka consumer).
     */
    public void handleStepFailure(BookingSagaState state, String errorMessage) {
        state.setErrorMessage(errorMessage);
        state.setFailedStep(state.getCurrentStep());

        if (state.canRetry()) {
            state.incrementRetry();
            sagaStateRepository.save(state);

            log.warn("Saga {}: Step {} failed, retrying (attempt {}). Error: {}",
                    state.getSagaId(), state.getCurrentStep(), state.getRetryCount(), errorMessage);

            // Retry the same step
            executeNextStep(state);
        } else {
            log.error("Saga {}: Step {} failed after max retries. Starting compensation. Error: {}",
                    state.getSagaId(), state.getCurrentStep(), errorMessage);

            startCompensation(state);
        }
    }

    /**
     * Start compensation (rollback) process.
     */
    public void startCompensation(BookingSagaState state) {
        state.setStatus(SagaStatus.COMPENSATING);
        state.setLastUpdatedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        log.warn("Saga {}: Starting compensation process", state.getSagaId());

        // Send compensation started event
        SagaEvent event = sagaEventProducer.createCommandEvent(state, SagaEvent.SagaEventType.COMPENSATE_STARTED);
        sagaEventProducer.sendCompensationCommand(event);

        executeNextCompensation(state);
    }

    /**
     * Execute the next compensation step.
     */
    private void executeNextCompensation(BookingSagaState state) {
        SagaStep compensationStep = state.getNextCompensatingStep();

        if (compensationStep == null) {
            completeCompensation(state);
            return;
        }

        SagaEvent.SagaEventType commandType;

        switch (compensationStep) {
            case REFUND_PAYMENT:
                commandType = SagaEvent.SagaEventType.REFUND_PAYMENT_CMD;
                break;
            case RELEASE_SEATS:
                commandType = SagaEvent.SagaEventType.RELEASE_SEATS_CMD;
                break;
            case CANCEL_BOOKING:
                commandType = SagaEvent.SagaEventType.CANCEL_BOOKING_CMD;
                break;
            default:
                log.warn("Unknown compensation step: {}", compensationStep);
                completeCompensation(state);
                return;
        }

        state.setCurrentStep(compensationStep);
        state.markWaitingForResponse(compensationStep);
        sagaStateRepository.save(state);

        // Send compensation command via Kafka
        SagaEvent command = sagaEventProducer.createCommandEvent(state, commandType);
        sagaEventProducer.sendCompensationCommand(command);

        log.warn("Saga {}: Sent compensation command {}", state.getSagaId(), commandType);
    }

    /**
     * Handle successful compensation step.
     */
    public void handleCompensationSuccess(BookingSagaState state, SagaStep compensatedStep) {
        state.markStepCompleted(compensatedStep);
        sagaStateRepository.save(state);

        log.info("Saga {}: Compensation step {} completed", state.getSagaId(), compensatedStep);

        // Continue with next compensation
        executeNextCompensation(state);
    }

    /**
     * Handle compensation failure.
     */
    public void handleCompensationFailure(BookingSagaState state, String errorMessage) {
        log.error("Saga {}: Compensation step {} failed: {}. Manual intervention required!",
                state.getSagaId(), state.getCurrentStep(), errorMessage);

        state.setStatus(SagaStatus.FAILED);
        state.setErrorMessage("Compensation failed: " + errorMessage);
        state.setCompletedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        // Alert operations team for manual intervention
        alertService.sendSagaCompensationFailedAlert(state);
    }

    /**
     * Complete the saga successfully.
     */
    private void completeSaga(BookingSagaState state) {
        state.setStatus(SagaStatus.COMPLETED);
        state.setCompletedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        log.info("Saga {} completed successfully. PNR: {}", state.getSagaId(), state.getBookingReference());

        // Send saga completed event
        SagaEvent event = sagaEventProducer.createSuccessReply(state, SagaEvent.SagaEventType.SAGA_COMPLETED);
        sagaEventProducer.sendReply(event);
    }

    /**
     * Complete compensation (rollback finished).
     */
    public void completeCompensation(BookingSagaState state) {
        state.setStatus(SagaStatus.ROLLED_BACK);
        state.setCompletedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        log.warn("Saga {} fully rolled back", state.getSagaId());

        // Send compensation completed event
        SagaEvent event = sagaEventProducer.createSuccessReply(state, SagaEvent.SagaEventType.COMPENSATION_COMPLETED);
        sagaEventProducer.sendReply(event);
    }

    /**
     * Execute release seats compensation (can be called directly or from Kafka).
     */
    public void executeReleaseSeats(BookingSagaState state) {
        log.info("Saga {}: Releasing seat reservation {}", state.getSagaId(), state.getSeatReservationId());

        try {
            // In production: call seat service to release
            // seatService.releaseReservation(state.getSeatReservationId());

            // Simulate async processing
            SagaEvent reply = sagaEventProducer.createSuccessReply(state, SagaEvent.SagaEventType.RELEASE_SEATS_SUCCESS);
            sagaEventProducer.sendReply(reply);

        } catch (Exception e) {
            SagaEvent reply = sagaEventProducer.createFailureReply(state,
                    SagaEvent.SagaEventType.RELEASE_SEATS_FAILED, e.getMessage());
            sagaEventProducer.sendReply(reply);
        }
    }

    /**
     * Execute refund payment compensation.
     */
    public void executeRefundPayment(BookingSagaState state) {
        log.info("Saga {}: Refunding payment {}", state.getSagaId(), state.getPaymentId());

        try {
            // In production: call payment gateway to refund
            // paymentService.refund(state.getPaymentId(), state.getTotalAmount());

            SagaEvent reply = sagaEventProducer.createSuccessReply(state, SagaEvent.SagaEventType.REFUND_PAYMENT_SUCCESS);
            sagaEventProducer.sendReply(reply);

        } catch (Exception e) {
            SagaEvent reply = sagaEventProducer.createFailureReply(state,
                    SagaEvent.SagaEventType.REFUND_PAYMENT_FAILED, e.getMessage());
            sagaEventProducer.sendReply(reply);
        }
    }

    /**
     * Execute cancel booking compensation.
     */
    public void executeCancelBooking(BookingSagaState state) {
        log.info("Saga {}: Cancelling booking {}", state.getSagaId(), state.getBookingReference());

        try {
            // In production: mark booking as cancelled in DB
            // bookingRepository.cancel(state.getBookingReference());

            SagaEvent reply = sagaEventProducer.createSuccessReply(state, SagaEvent.SagaEventType.CANCEL_BOOKING_SUCCESS);
            sagaEventProducer.sendReply(reply);

        } catch (Exception e) {
            SagaEvent reply = sagaEventProducer.createFailureReply(state,
                    SagaEvent.SagaEventType.CANCEL_BOOKING_FAILED, e.getMessage());
            sagaEventProducer.sendReply(reply);
        }
    }

    // ==================== Recovery & Monitoring ====================

    /**
     * Recover timed out sagas - runs every minute.
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverTimedOutSagas() {
        List<BookingSagaState> timedOutSagas = sagaStateRepository.findTimedOutSagas();

        for (BookingSagaState state : timedOutSagas) {
            log.warn("Saga {} timed out at step {}. Starting compensation.",
                    state.getSagaId(), state.getCurrentStep());

            // Send timeout alert
            alertService.sendSagaTimeoutAlert(state);

            state.setErrorMessage("Saga timed out");
            startCompensation(state);
        }

        if (!timedOutSagas.isEmpty()) {
            log.info("Recovered {} timed out sagas", timedOutSagas.size());
        }
    }

    /**
     * Retry stuck compensating sagas - runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000)
    public void retryStuckCompensations() {
        List<BookingSagaState> compensatingSagas = sagaStateRepository.findCompensatingSagas();

        for (BookingSagaState state : compensatingSagas) {
            if (state.getLastUpdatedAt() != null &&
                    state.getLastUpdatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {

                log.warn("Saga {} stuck in compensation. Retrying.", state.getSagaId());
                executeNextCompensation(state);
            }
        }
    }

    // ==================== Helper Methods ====================

    private SagaStep getNextStep(SagaStep currentStep) {
        switch (currentStep) {
            case VALIDATE_FLIGHT:
                return SagaStep.RESERVE_SEATS;
            case RESERVE_SEATS:
                return SagaStep.VALIDATE_PASSENGERS;
            case VALIDATE_PASSENGERS:
                return SagaStep.CALCULATE_PRICE;
            case CALCULATE_PRICE:
                return SagaStep.PROCESS_PAYMENT;
            case PROCESS_PAYMENT:
                return SagaStep.CREATE_BOOKING;
            case CREATE_BOOKING:
                return SagaStep.SEND_CONFIRMATION;
            case SEND_CONFIRMATION:
                return SagaStep.COMPLETE;
            default:
                return SagaStep.COMPLETE;
        }
    }

    /**
     * Get saga state for monitoring.
     */
    public Optional<BookingSagaState> getSagaState(String sagaId) {
        return sagaStateRepository.findById(sagaId);
    }

    /**
     * Get saga by PNR.
     */
    public Optional<BookingSagaState> getSagaByPNR(String pnr) {
        return sagaStateRepository.findByPNR(pnr);
    }

    /**
     * Get all active sagas.
     */
    public List<BookingSagaState> getActiveSagas() {
        return sagaStateRepository.findActiveSagas();
    }

    /**
     * Get saga statistics.
     */
    public SagaStateRepository.SagaStats getStats() {
        return sagaStateRepository.getStats();
    }
}
