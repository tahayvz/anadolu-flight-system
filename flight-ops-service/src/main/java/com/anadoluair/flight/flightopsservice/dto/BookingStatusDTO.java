package com.anadoluair.flight.flightopsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for booking status updates sent via WebSocket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusDTO {

    private String bookingReference;
    private String flightNumber;
    private String eventType;
    private String status;
    private int passengerCount;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime flightDate;
    private LocalDateTime lastUpdated;
}
