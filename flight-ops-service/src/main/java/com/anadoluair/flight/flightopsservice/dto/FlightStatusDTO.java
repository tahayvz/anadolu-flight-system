package com.anadoluair.flight.flightopsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for flight status updates sent via WebSocket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightStatusDTO {

    private String flightNumber;
    private String airline;
    private String origin;
    private String destination;
    private String status;
    private String gate;
    private String terminal;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime estimatedDeparture;
    private LocalDateTime scheduledArrival;
    private LocalDateTime estimatedArrival;
    private String aircraft;
    private String remarks;
    private LocalDateTime lastUpdated;
}
