package com.anadoluair.flight.flightopsservice.kafka;

import com.anadoluair.flight.events.FlightEvent;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.flightopsservice.dto.FlightStatusDTO;
import com.anadoluair.flight.flightopsservice.websocket.FlightWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka Consumer for processing flight events from integration-service.
 * Receives events and broadcasts them to WebSocket clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightEventConsumer {

    private final FlightWebSocketHandler webSocketHandler;

    /**
     * Listen for all flight events and broadcast to WebSocket clients.
     */
    @KafkaListener(
            topics = KafkaTopics.FLIGHT_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeFlightEvent(FlightEvent event) {
        log.info("Received flight event: type={}, flight={}, eventId={}",
                event.getEventType(),
                event.getFlightData().getFlightNumber(),
                event.getEventId());

        // Convert to DTO and broadcast
        FlightStatusDTO status = mapToDTO(event);
        webSocketHandler.broadcastFlightUpdate(status);

        // Also send to flight-specific topic
        webSocketHandler.sendFlightUpdate(event.getFlightData().getFlightNumber(), status);
    }

    /**
     * Listen for flight status updates (higher priority, separate topic).
     */
    @KafkaListener(
            topics = KafkaTopics.FLIGHT_STATUS_UPDATES,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStatusUpdate(FlightEvent event) {
        log.info("Received status update: flight={}, status={}",
                event.getFlightData().getFlightNumber(),
                event.getFlightData().getStatus());

        FlightStatusDTO status = mapToDTO(event);
        status.setRemarks("Status update from operations");

        // Broadcast to all and specific flight topic
        webSocketHandler.broadcastFlightUpdate(status);
        webSocketHandler.sendFlightUpdate(event.getFlightData().getFlightNumber(), status);
    }

    /**
     * Map FlightEvent to FlightStatusDTO.
     */
    private FlightStatusDTO mapToDTO(FlightEvent event) {
        FlightEvent.FlightData data = event.getFlightData();

        return FlightStatusDTO.builder()
                .flightNumber(data.getFlightNumber())
                .airline(data.getAirline())
                .origin(data.getOrigin())
                .destination(data.getDestination())
                .status(data.getStatus() != null ? data.getStatus().name() : "UNKNOWN")
                .gate(data.getGate())
                .terminal(data.getTerminal())
                .scheduledDeparture(data.getScheduledDeparture())
                .estimatedDeparture(data.getEstimatedDeparture())
                .scheduledArrival(data.getScheduledArrival())
                .estimatedArrival(data.getEstimatedArrival())
                .aircraft(data.getAircraft())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}
