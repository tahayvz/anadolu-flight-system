package com.anadoluair.flight.bookingservice.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * State object for Booking Saga - stored in Redis for persistence.
 * Tracks the progress of a booking through multiple steps.
 *
 * Saga Steps:
 * 1. VALIDATE_FLIGHT - Check flight availability
 * 2. RESERVE_SEATS - Temporarily hold seats
 * 3. VALIDATE_PASSENGERS - Verify passenger data
 * 4. CALCULATE_PRICE - Calculate total price
 * 5. PROCESS_PAYMENT - Charge payment method
 * 6. CREATE_BOOKING - Persist booking
 * 7. SEND_CONFIRMATION - Send email/SMS
 * 8. COMPLETE - Saga completed successfully
 *
 * Compensating actions (rollback):
 * - RELEASE_SEATS - Release held seats
 * - REFUND_PAYMENT - Reverse payment
 * - CANCEL_BOOKING - Mark booking as cancelled
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingSagaState implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_RETRIES = 3;

    private String sagaId;
    private String bookingReference;
    private SagaStatus status;
    private SagaStep currentStep;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastUpdatedAt;

    // Booking data
    private String flightNumber;
    private String flightDate;
    private int passengerCount;
    private BigDecimal totalAmount;
    private String currency;
    private String paymentId;
    private String paymentMethod;
    private String seatReservationId;

    // Contact info for notifications
    private String contactEmail;
    private String contactPhone;

    // Error handling
    private String errorMessage;
    private SagaStep failedStep;
    private int retryCount;

    // Correlation for distributed tracing
    private String correlationId;
    private String traceId;
    private String spanId;

    // Completed steps for rollback
    @Builder.Default
    private List<SagaStep> completedSteps = new ArrayList<>();

    public enum SagaStatus {
        STARTED,
        IN_PROGRESS,
        WAITING_FOR_RESPONSE,
        COMPENSATING,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }

    public enum SagaStep {
        VALIDATE_FLIGHT,
        RESERVE_SEATS,
        VALIDATE_PASSENGERS,
        CALCULATE_PRICE,
        PROCESS_PAYMENT,
        CREATE_BOOKING,
        SEND_CONFIRMATION,
        COMPLETE,
        // Compensating steps
        RELEASE_SEATS,
        REFUND_PAYMENT,
        CANCEL_BOOKING
    }

    public void markStepCompleted(SagaStep step) {
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }
        completedSteps.add(step);
        currentStep = step;
        lastUpdatedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    public void incrementRetry() {
        retryCount++;
        lastUpdatedAt = LocalDateTime.now();
    }

    public boolean needsCompensation() {
        return completedSteps != null && !completedSteps.isEmpty() && status == SagaStatus.COMPENSATING;
    }

    /**
     * Check if saga has timed out (default 5 minutes).
     */
    public boolean isTimedOut() {
        if (startedAt == null) return false;
        return startedAt.plusMinutes(5).isBefore(LocalDateTime.now())
                && status != SagaStatus.COMPLETED
                && status != SagaStatus.ROLLED_BACK
                && status != SagaStatus.FAILED;
    }

    /**
     * Get the next compensating step based on completed steps.
     * Compensation happens in reverse order.
     */
    public SagaStep getNextCompensatingStep() {
        if (completedSteps == null || completedSteps.isEmpty()) {
            return null;
        }

        // Check in reverse order and return appropriate compensation
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            SagaStep step = completedSteps.get(i);
            switch (step) {
                case PROCESS_PAYMENT:
                    if (!isCompensationDone(SagaStep.REFUND_PAYMENT)) {
                        return SagaStep.REFUND_PAYMENT;
                    }
                    break;
                case RESERVE_SEATS:
                    if (!isCompensationDone(SagaStep.RELEASE_SEATS)) {
                        return SagaStep.RELEASE_SEATS;
                    }
                    break;
                case CREATE_BOOKING:
                    if (!isCompensationDone(SagaStep.CANCEL_BOOKING)) {
                        return SagaStep.CANCEL_BOOKING;
                    }
                    break;
                default:
                    // No compensation needed for validation steps
                    break;
            }
        }
        return null;
    }

    private boolean isCompensationDone(SagaStep compensationStep) {
        return completedSteps != null && completedSteps.contains(compensationStep);
    }

    /**
     * Mark saga as waiting for async response from Kafka.
     */
    public void markWaitingForResponse(SagaStep step) {
        this.currentStep = step;
        this.status = SagaStatus.WAITING_FOR_RESPONSE;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Get Redis key for this saga state.
     */
    public String getRedisKey() {
        return "saga:" + sagaId;
    }

    /**
     * Create a summary for logging.
     */
    public String toSummary() {
        return String.format("Saga[id=%s, status=%s, step=%s, pnr=%s, retries=%d]",
                sagaId, status, currentStep, bookingReference, retryCount);
    }
}
