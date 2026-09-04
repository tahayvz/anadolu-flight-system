package com.anadoluair.flight.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Saga Event for distributed transaction coordination via Kafka.
 *
 * Used for:
 * - Triggering saga steps across services
 * - Communicating step completion/failure
 * - Initiating compensation (rollback)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String sagaId;
    private SagaEventType eventType;
    private SagaStepType currentStep;
    private LocalDateTime timestamp;

    // Booking context
    private String bookingReference;
    private String flightNumber;
    private String flightDate;
    private int passengerCount;
    private BigDecimal totalAmount;
    private String currency;

    // Step-specific data
    private String seatReservationId;
    private String paymentId;
    private String paymentMethod;

    // Passenger data for validation
    private List<PassengerData> passengers;

    // Error handling
    private String errorMessage;
    private String failedStep;
    private int retryCount;

    // Correlation for tracing
    private String correlationId;
    private String sourceService;

    // Additional metadata
    private Map<String, String> metadata;

    /**
     * Saga event types for Kafka communication.
     */
    public enum SagaEventType {
        // Saga lifecycle
        SAGA_STARTED,
        SAGA_COMPLETED,
        SAGA_FAILED,

        // Step commands (request to execute)
        VALIDATE_FLIGHT_CMD,
        RESERVE_SEATS_CMD,
        VALIDATE_PASSENGERS_CMD,
        CALCULATE_PRICE_CMD,
        PROCESS_PAYMENT_CMD,
        CREATE_BOOKING_CMD,
        SEND_CONFIRMATION_CMD,

        // Step results (response after execution)
        VALIDATE_FLIGHT_SUCCESS,
        VALIDATE_FLIGHT_FAILED,
        RESERVE_SEATS_SUCCESS,
        RESERVE_SEATS_FAILED,
        VALIDATE_PASSENGERS_SUCCESS,
        VALIDATE_PASSENGERS_FAILED,
        CALCULATE_PRICE_SUCCESS,
        CALCULATE_PRICE_FAILED,
        PROCESS_PAYMENT_SUCCESS,
        PROCESS_PAYMENT_FAILED,
        CREATE_BOOKING_SUCCESS,
        CREATE_BOOKING_FAILED,
        SEND_CONFIRMATION_SUCCESS,
        SEND_CONFIRMATION_FAILED,

        // Compensation commands
        COMPENSATE_STARTED,
        RELEASE_SEATS_CMD,
        REFUND_PAYMENT_CMD,
        CANCEL_BOOKING_CMD,

        // Compensation results
        RELEASE_SEATS_SUCCESS,
        RELEASE_SEATS_FAILED,
        REFUND_PAYMENT_SUCCESS,
        REFUND_PAYMENT_FAILED,
        CANCEL_BOOKING_SUCCESS,
        CANCEL_BOOKING_FAILED,
        COMPENSATION_COMPLETED
    }

    /**
     * Saga step types.
     */
    public enum SagaStepType {
        VALIDATE_FLIGHT,
        RESERVE_SEATS,
        VALIDATE_PASSENGERS,
        CALCULATE_PRICE,
        PROCESS_PAYMENT,
        CREATE_BOOKING,
        SEND_CONFIRMATION,
        COMPLETE,
        // Compensation steps
        RELEASE_SEATS,
        REFUND_PAYMENT,
        CANCEL_BOOKING
    }

    /**
     * Passenger data for saga context.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String firstName;
        private String lastName;
        private String dateOfBirth;
        private String passengerType;
        private String documentType;
        private String documentNumber;
        private String nationality;
        private String email;
        private String phone;
    }
}
