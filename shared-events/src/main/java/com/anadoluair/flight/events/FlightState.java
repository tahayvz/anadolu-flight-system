package com.anadoluair.flight.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents the current state of a flight.
 * Used for realistic flight lifecycle simulation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightState {

    private String flightNumber;
    private String airline;
    private String origin;
    private String destination;
    private FlightEvent.FlightStatus status;
    private String gate;
    private String terminal;
    private String aircraft;

    // Scheduled times (from flight plan)
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;

    // Estimated times (updated during operations)
    private LocalDateTime estimatedDeparture;
    private LocalDateTime estimatedArrival;

    // Actual times (recorded when event occurs)
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;

    // Seat management
    private int totalSeats;
    private int bookedSeats;

    private LocalDateTime lastUpdated;

    // Flight lifecycle duration constants (in minutes)
    public static final int BOARDING_DURATION = 45;      // Boarding starts 45 min before departure
    public static final int TAXI_DURATION = 15;          // Taxi after departure
    public static final int ARRIVAL_PROCESSING = 20;     // Landing to gate arrival

    /**
     * Check if the flight can accept new bookings.
     */
    public boolean isBookable() {
        return status == FlightEvent.FlightStatus.SCHEDULED
            || status == FlightEvent.FlightStatus.BOARDING;
    }

    /**
     * Get available seats for booking.
     */
    public int getAvailableSeats() {
        return totalSeats - bookedSeats;
    }

    /**
     * Check if the flight has available seats.
     */
    public boolean hasAvailableSeats(int required) {
        return getAvailableSeats() >= required;
    }
}
