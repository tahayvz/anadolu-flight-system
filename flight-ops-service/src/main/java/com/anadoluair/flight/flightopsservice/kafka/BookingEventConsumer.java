package com.anadoluair.flight.flightopsservice.kafka;

import com.anadoluair.flight.events.BookingEvent;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.flightopsservice.dto.BookingStatusDTO;
import com.anadoluair.flight.flightopsservice.websocket.FlightWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka Consumer for processing booking events from booking-service.
 * Receives events and broadcasts them to WebSocket clients for real-time updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final FlightWebSocketHandler webSocketHandler;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "bookingEventListenerContainerFactory"
    )
    public void consumeBookingEvent(BookingEvent event) {
        log.info("Received booking event: type={}, pnr={}, flight={}, eventId={}",
                event.getEventType(),
                event.getBookingData().getBookingReference(),
                event.getBookingData().getFlightNumber(),
                event.getEventId());

        BookingStatusDTO status = mapToDTO(event);

        // Broadcast to all booking subscribers
        webSocketHandler.broadcastBookingUpdate(status);

        // Also send to flight-specific booking topic
        webSocketHandler.sendFlightBookingUpdate(event.getBookingData().getFlightNumber(), status);
    }

    private BookingStatusDTO mapToDTO(BookingEvent event) {
        BookingEvent.BookingData data = event.getBookingData();

        return BookingStatusDTO.builder()
                .bookingReference(data.getBookingReference())
                .flightNumber(data.getFlightNumber())
                .eventType(event.getEventType() != null ? event.getEventType().name() : "UNKNOWN")
                .status(data.getStatus() != null ? data.getStatus().name() : "UNKNOWN")
                .passengerCount(data.getPassengers() != null ? data.getPassengers().size() : 0)
                .totalAmount(data.getTotalAmount())
                .currency(data.getCurrency())
                .flightDate(data.getFlightDate())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}
