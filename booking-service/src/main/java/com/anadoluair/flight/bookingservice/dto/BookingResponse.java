package com.anadoluair.flight.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for booking response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private String bookingReference;  // PNR
    private String flightNumber;
    private LocalDate flightDate;
    private String origin;
    private String destination;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String contactEmail;
    private String contactPhone;
    private List<PassengerInfo> passengers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {
        private Long id;
        private String firstName;
        private String lastName;
        private LocalDate dateOfBirth;
        private String passengerType;
        private String seatNumber;
        private String ticketNumber;
    }
}
