package com.anadoluair.flight.bookingservice.saga;

import com.anadoluair.flight.bookingservice.client.FlightOpsClient;
import com.anadoluair.flight.bookingservice.entity.Booking;
import com.anadoluair.flight.bookingservice.repository.BookingRepository;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.events.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles Saga commands and executes the actual business logic.
 *
 * In a real microservices setup, each command would be handled
 * by its respective service:
 * - VALIDATE_FLIGHT_CMD → flight-ops-service
 * - RESERVE_SEATS_CMD → seat-service
 * - PROCESS_PAYMENT_CMD → payment-service
 * - etc.
 *
 * For this demo, we handle all commands in booking-service
 * to show the complete flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.kafka.consumer.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class SagaCommandHandler {

    private final SagaEventProducer sagaEventProducer;
    private final SagaStateRepository sagaStateRepository;
    private final FlightOpsClient flightOpsClient;
    private final BookingRepository bookingRepository;

    /**
     * Listen for saga commands and execute them.
     */
    @KafkaListener(
            topics = KafkaTopics.SAGA_COMMANDS,
            groupId = "booking-saga-command-handler",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSagaCommand(ConsumerRecord<String, SagaEvent> record, Acknowledgment ack) {
        SagaEvent event = record.value();
        log.info("Received saga command: sagaId={}, type={}", event.getSagaId(), event.getEventType());

        try {
            switch (event.getEventType()) {
                case VALIDATE_FLIGHT_CMD:
                    handleValidateFlight(event);
                    break;
                case RESERVE_SEATS_CMD:
                    handleReserveSeats(event);
                    break;
                case VALIDATE_PASSENGERS_CMD:
                    handleValidatePassengers(event);
                    break;
                case CALCULATE_PRICE_CMD:
                    handleCalculatePrice(event);
                    break;
                case PROCESS_PAYMENT_CMD:
                    handleProcessPayment(event);
                    break;
                case CREATE_BOOKING_CMD:
                    handleCreateBooking(event);
                    break;
                case SEND_CONFIRMATION_CMD:
                    handleSendConfirmation(event);
                    break;
                default:
                    log.warn("Unknown command type: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle saga command: {}", event.getEventType(), e);
            // Send failure reply
            sendFailureReply(event, e.getMessage());
            ack.acknowledge();
        }
    }

    /**
     * Step 1: Validate flight availability.
     */
    private void handleValidateFlight(SagaEvent event) {
        log.info("Saga {}: Validating flight {}", event.getSagaId(), event.getFlightNumber());

        try {
            // Call flight-ops-service to validate
            FlightOpsClient.FlightBookingInfo flightInfo = flightOpsClient.getFlightBookingInfo(event.getFlightNumber());

            if (flightInfo == null) {
                sendFailureReply(event, "Flight not found: " + event.getFlightNumber());
                return;
            }

            if (flightInfo.availableSeats() < event.getPassengerCount()) {
                sendFailureReply(event, String.format("Not enough seats. Available: %d, Requested: %d",
                        flightInfo.availableSeats(), event.getPassengerCount()));
                return;
            }

            // Flight is valid
            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.VALIDATE_FLIGHT_SUCCESS);
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Flight validation successful", event.getSagaId());

        } catch (Exception e) {
            log.error("Saga {}: Flight validation failed", event.getSagaId(), e);
            sendFailureReply(event, "Flight validation error: " + e.getMessage());
        }
    }

    /**
     * Step 2: Reserve seats temporarily.
     */
    private void handleReserveSeats(SagaEvent event) {
        log.info("Saga {}: Reserving {} seats for flight {}",
                event.getSagaId(), event.getPassengerCount(), event.getFlightNumber());

        try {
            // In production: call seat-service to create temporary reservation
            // SeatReservation reservation = seatService.reserve(flightNumber, passengerCount, 15);

            // Simulate seat reservation
            String seatReservationId = "SEAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.RESERVE_SEATS_SUCCESS);
            reply.setSeatReservationId(seatReservationId);
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Seats reserved with ID: {}", event.getSagaId(), seatReservationId);

        } catch (Exception e) {
            log.error("Saga {}: Seat reservation failed", event.getSagaId(), e);
            sendFailureReply(event, "Seat reservation error: " + e.getMessage());
        }
    }

    /**
     * Step 3: Validate passenger information.
     */
    private void handleValidatePassengers(SagaEvent event) {
        log.info("Saga {}: Validating {} passengers", event.getSagaId(), event.getPassengerCount());

        try {
            // In production: validate passenger documents, check no-fly lists, etc.
            // passengerValidationService.validateAll(event.getPassengers());

            // Simulate validation
            if (event.getPassengers() != null) {
                for (SagaEvent.PassengerData passenger : event.getPassengers()) {
                    if (passenger.getFirstName() == null || passenger.getLastName() == null) {
                        sendFailureReply(event, "Invalid passenger data: missing name");
                        return;
                    }
                }
            }

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.VALIDATE_PASSENGERS_SUCCESS);
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Passenger validation successful", event.getSagaId());

        } catch (Exception e) {
            log.error("Saga {}: Passenger validation failed", event.getSagaId(), e);
            sendFailureReply(event, "Passenger validation error: " + e.getMessage());
        }
    }

    /**
     * Step 4: Calculate total price.
     */
    private void handleCalculatePrice(SagaEvent event) {
        log.info("Saga {}: Calculating price for {} passengers", event.getSagaId(), event.getPassengerCount());

        try {
            // In production: call pricing-service with complex calculations
            // PriceCalculation price = pricingService.calculate(flightNumber, passengers, promotions);

            // Simulate pricing (2500 TRY per passenger)
            BigDecimal basePrice = BigDecimal.valueOf(2500);
            BigDecimal totalAmount = basePrice.multiply(BigDecimal.valueOf(event.getPassengerCount()));

            // Add taxes (18%)
            BigDecimal taxes = totalAmount.multiply(BigDecimal.valueOf(0.18));
            totalAmount = totalAmount.add(taxes);

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.CALCULATE_PRICE_SUCCESS);
            reply.setTotalAmount(totalAmount);
            reply.setCurrency("TRY");
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Price calculated: {} TRY", event.getSagaId(), totalAmount);

        } catch (Exception e) {
            log.error("Saga {}: Price calculation failed", event.getSagaId(), e);
            sendFailureReply(event, "Price calculation error: " + e.getMessage());
        }
    }

    /**
     * Step 5: Process payment.
     */
    private void handleProcessPayment(SagaEvent event) {
        log.info("Saga {}: Processing payment of {} {}",
                event.getSagaId(), event.getTotalAmount(), event.getCurrency());

        try {
            // In production: call payment gateway
            // PaymentResult result = paymentGateway.charge(paymentMethod, totalAmount, currency);

            // Simulate payment processing (5% random failure for testing)
            if (Math.random() < 0.05) {
                sendFailureReply(event, "Payment declined by bank");
                return;
            }

            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.PROCESS_PAYMENT_SUCCESS);
            reply.setPaymentId(paymentId);
            reply.setTotalAmount(event.getTotalAmount());
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Payment processed with ID: {}", event.getSagaId(), paymentId);

        } catch (Exception e) {
            log.error("Saga {}: Payment processing failed", event.getSagaId(), e);
            sendFailureReply(event, "Payment error: " + e.getMessage());
        }
    }

    /**
     * Step 6: Create booking record in database.
     */
    private void handleCreateBooking(SagaEvent event) {
        log.info("Saga {}: Creating booking record", event.getSagaId());

        try {
            // Generate PNR
            String pnr = generatePNR();

            // Create booking entity
            Booking booking = new Booking();
            booking.setBookingReference(pnr);
            booking.setFlightNumber(event.getFlightNumber());
            booking.setFlightDate(event.getFlightDate() != null ?
                    java.time.LocalDate.parse(event.getFlightDate()) : java.time.LocalDate.now().plusDays(30));
            booking.setStatus(Booking.BookingStatus.CONFIRMED);
            booking.setTotalAmount(event.getTotalAmount());
            booking.setCurrency(event.getCurrency() != null ? event.getCurrency() : "TRY");
            booking.setPaymentId(event.getPaymentId());
            booking.setSeatReservationId(event.getSeatReservationId());
            booking.setOrigin("IST"); // Default origin
            booking.setDestination("---"); // Will be updated from flight info
            // createdAt is set by @PrePersist

            // Save to database
            bookingRepository.save(booking);

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.CREATE_BOOKING_SUCCESS);
            reply.setBookingReference(pnr);
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Booking created with PNR: {}", event.getSagaId(), pnr);

        } catch (Exception e) {
            log.error("Saga {}: Booking creation failed", event.getSagaId(), e);
            sendFailureReply(event, "Booking creation error: " + e.getMessage());
        }
    }

    /**
     * Step 7: Send confirmation to customer.
     */
    private void handleSendConfirmation(SagaEvent event) {
        log.info("Saga {}: Sending confirmation for PNR {}", event.getSagaId(), event.getBookingReference());

        try {
            // In production: call notification-service
            // notificationService.sendBookingConfirmation(pnr, email, phone);

            // Simulate sending email/SMS
            log.info("Saga {}: Sending email confirmation to customer", event.getSagaId());
            log.info("Saga {}: Sending SMS confirmation to customer", event.getSagaId());

            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.SEND_CONFIRMATION_SUCCESS);
            sagaEventProducer.sendReply(reply);

            log.info("Saga {}: Confirmation sent successfully", event.getSagaId());

        } catch (Exception e) {
            log.error("Saga {}: Sending confirmation failed", event.getSagaId(), e);
            // Confirmation failure is non-critical, we can still complete the saga
            // but log the error for manual follow-up
            SagaEvent reply = createSuccessReply(event, SagaEvent.SagaEventType.SEND_CONFIRMATION_SUCCESS);
            sagaEventProducer.sendReply(reply);
        }
    }

    // ==================== Helper Methods ====================

    private SagaEvent createSuccessReply(SagaEvent command, SagaEvent.SagaEventType successType) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(command.getSagaId())
                .eventType(successType)
                .currentStep(command.getCurrentStep())
                .timestamp(LocalDateTime.now())
                .bookingReference(command.getBookingReference())
                .flightNumber(command.getFlightNumber())
                .flightDate(command.getFlightDate())
                .passengerCount(command.getPassengerCount())
                .seatReservationId(command.getSeatReservationId())
                .paymentId(command.getPaymentId())
                .totalAmount(command.getTotalAmount())
                .currency(command.getCurrency())
                .correlationId(command.getCorrelationId())
                .sourceService("booking-service")
                .build();
    }

    private void sendFailureReply(SagaEvent command, String errorMessage) {
        SagaEvent.SagaEventType failureType = getFailureTypeForCommand(command.getEventType());

        SagaEvent reply = SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(command.getSagaId())
                .eventType(failureType)
                .currentStep(command.getCurrentStep())
                .timestamp(LocalDateTime.now())
                .flightNumber(command.getFlightNumber())
                .errorMessage(errorMessage)
                .failedStep(command.getCurrentStep() != null ? command.getCurrentStep().name() : null)
                .correlationId(command.getCorrelationId())
                .sourceService("booking-service")
                .build();

        sagaEventProducer.sendReply(reply);
    }

    private SagaEvent.SagaEventType getFailureTypeForCommand(SagaEvent.SagaEventType commandType) {
        switch (commandType) {
            case VALIDATE_FLIGHT_CMD:
                return SagaEvent.SagaEventType.VALIDATE_FLIGHT_FAILED;
            case RESERVE_SEATS_CMD:
                return SagaEvent.SagaEventType.RESERVE_SEATS_FAILED;
            case VALIDATE_PASSENGERS_CMD:
                return SagaEvent.SagaEventType.VALIDATE_PASSENGERS_FAILED;
            case CALCULATE_PRICE_CMD:
                return SagaEvent.SagaEventType.CALCULATE_PRICE_FAILED;
            case PROCESS_PAYMENT_CMD:
                return SagaEvent.SagaEventType.PROCESS_PAYMENT_FAILED;
            case CREATE_BOOKING_CMD:
                return SagaEvent.SagaEventType.CREATE_BOOKING_FAILED;
            case SEND_CONFIRMATION_CMD:
                return SagaEvent.SagaEventType.SEND_CONFIRMATION_FAILED;
            default:
                return SagaEvent.SagaEventType.SAGA_FAILED;
        }
    }

    private String generatePNR() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder pnr = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            pnr.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return pnr.toString();
    }
}
