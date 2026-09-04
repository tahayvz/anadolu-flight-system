package com.anadoluair.flight.integrationservice.kafka;

import com.anadoluair.flight.events.BookingEvent;
import com.anadoluair.flight.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for processing booking events in the integration layer.
 * Handles forwarding booking events to external systems (notifications, partner APIs, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "bookingEventListenerContainerFactory"
    )
    public void consumeBookingEvent(BookingEvent event) {
        log.info("Received booking event for external processing: type={}, pnr={}, flight={}",
                event.getEventType(),
                event.getBookingData().getBookingReference(),
                event.getBookingData().getFlightNumber());

        switch (event.getEventType()) {
            case BOOKING_CREATED -> handleBookingCreated(event);
            case BOOKING_CONFIRMED -> handleBookingConfirmed(event);
            case BOOKING_CANCELLED -> handleBookingCancelled(event);
            case BOOKING_MODIFIED -> handleBookingModified(event);
            case CHECK_IN_COMPLETED -> handleCheckInCompleted(event);
            case SEAT_SELECTED -> handleSeatSelected(event);
        }
    }

    private void handleBookingCreated(BookingEvent event) {
        log.info("Processing new booking for external systems: pnr={}, flight={}, passengers={}",
                event.getBookingData().getBookingReference(),
                event.getBookingData().getFlightNumber(),
                event.getBookingData().getPassengers() != null ? event.getBookingData().getPassengers().size() : 0);
        // TODO: Send confirmation email/SMS via external notification provider
        // TODO: Forward to partner airline systems if codeshare
    }

    private void handleBookingConfirmed(BookingEvent event) {
        log.info("Booking confirmed, triggering external notifications: pnr={}",
                event.getBookingData().getBookingReference());
        // TODO: Send e-ticket via email
        // TODO: Update loyalty/miles system
    }

    private void handleBookingCancelled(BookingEvent event) {
        log.info("Booking cancelled, notifying external systems: pnr={}",
                event.getBookingData().getBookingReference());
        // TODO: Send cancellation notification
        // TODO: Trigger refund via payment gateway
    }

    private void handleBookingModified(BookingEvent event) {
        log.info("Booking modified, syncing external systems: pnr={}",
                event.getBookingData().getBookingReference());
        // TODO: Send modification confirmation
    }

    private void handleCheckInCompleted(BookingEvent event) {
        log.info("Check-in completed, forwarding to airport systems: pnr={}",
                event.getBookingData().getBookingReference());
        // TODO: Forward to DCS (Departure Control System)
    }

    private void handleSeatSelected(BookingEvent event) {
        log.debug("Seat selected: pnr={}", event.getBookingData().getBookingReference());
        // TODO: Update seat map in external systems
    }
}
