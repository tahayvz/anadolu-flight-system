package com.anadoluair.flight.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base event for flight-related Kafka messages.
 * Published by integration-service, consumed by flight-ops-service and booking-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightEvent {

    private String eventId;
    private EventType eventType;
    private LocalDateTime timestamp;
    private FlightData flightData;

    public enum EventType {
        FLIGHT_CREATED,
        FLIGHT_UPDATED,
        FLIGHT_CANCELLED,
        FLIGHT_DELAYED,
        GATE_CHANGED,
        STATUS_CHANGED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlightData {
        private String flightNumber;      // ZZ1234
        private String airline;            // TK
        private String origin;             // IST
        private String destination;        // JFK
        private LocalDateTime scheduledDeparture;
        private LocalDateTime scheduledArrival;
        private LocalDateTime estimatedDeparture;
        private LocalDateTime estimatedArrival;
        private FlightStatus status;
        private String gate;
        private String terminal;
        private String aircraft;
    }

    public enum FlightStatus {
        SCHEDULED,
        BOARDING,
        DEPARTED,
        IN_FLIGHT,
        LANDED,
        ARRIVED,
        DELAYED,
        CANCELLED
    }
}
