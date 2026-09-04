package com.anadoluair.flight.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event for booking-related Kafka messages.
 * Published by booking-service, consumed by other services for notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {

    private String eventId;
    private EventType eventType;
    private LocalDateTime timestamp;
    private BookingData bookingData;

    public enum EventType {
        BOOKING_CREATED,
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED,
        BOOKING_MODIFIED,
        CHECK_IN_COMPLETED,
        SEAT_SELECTED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingData {
        private String bookingReference;   // PNR: ABC123
        private String flightNumber;
        private LocalDateTime flightDate;
        private List<PassengerInfo> passengers;
        private BookingStatus status;
        private BigDecimal totalAmount;
        private String currency;

        /**
         * Yolcuyla iletisim kurulacak e-posta.
         * <p>
         * Olayin icinde tasinir; tuketici servis, gondermek icin booking-service'in
         * veritabanina sormak zorunda kalmaz. Servisler arasi senkron cagri kurmak,
         * event-driven mimarinin cozmeye calistigi bagimliligi geri getirirdi.
         */
        private String contactEmail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {
        private String firstName;
        private String lastName;
        private String passengerType;      // ADULT, CHILD, INFANT
        private String seatNumber;
        private String ticketNumber;
    }

    public enum BookingStatus {
        PENDING,
        CONFIRMED,
        TICKETED,
        CHECKED_IN,
        BOARDED,
        COMPLETED,
        CANCELLED,
        NO_SHOW
    }
}
