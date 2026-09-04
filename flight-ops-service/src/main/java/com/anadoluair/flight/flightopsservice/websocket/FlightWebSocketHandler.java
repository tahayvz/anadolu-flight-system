package com.anadoluair.flight.flightopsservice.websocket;

import com.anadoluair.flight.flightopsservice.dto.BookingStatusDTO;
import com.anadoluair.flight.flightopsservice.dto.FlightStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket handler for broadcasting flight and booking updates to connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast flight update to all subscribers on /topic/flights
     */
    public void broadcastFlightUpdate(FlightStatusDTO status) {
        log.debug("Broadcasting flight update: {}", status.getFlightNumber());
        messagingTemplate.convertAndSend("/topic/flights", status);
    }

    /**
     * Send flight update to specific flight topic
     */
    public void sendFlightUpdate(String flightNumber, FlightStatusDTO status) {
        log.debug("Sending update for flight: {}", flightNumber);
        messagingTemplate.convertAndSend("/topic/flight/" + flightNumber, status);
    }

    /**
     * Broadcast booking update to all subscribers on /topic/bookings
     */
    public void broadcastBookingUpdate(BookingStatusDTO status) {
        log.debug("Broadcasting booking update: pnr={}, type={}", status.getBookingReference(), status.getEventType());
        messagingTemplate.convertAndSend("/topic/bookings", status);
    }

    /**
     * Send booking update to specific flight's booking topic
     */
    public void sendFlightBookingUpdate(String flightNumber, BookingStatusDTO status) {
        log.debug("Sending booking update for flight: {}", flightNumber);
        messagingTemplate.convertAndSend("/topic/flight/" + flightNumber + "/bookings", status);
    }

    /**
     * Broadcast status board update
     */
    public void broadcastStatusBoardUpdate(Object statusBoard) {
        log.debug("Broadcasting status board update");
        messagingTemplate.convertAndSend("/topic/status", statusBoard);
    }

    /**
     * Send private message to a specific user session
     */
    public void sendToUser(String sessionId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(sessionId, destination, payload);
    }
}
