package com.anadoluair.flight.bookingservice.exception;

/**
 * Exception thrown when attempting to book a flight that is not bookable.
 * This can happen when:
 * - Flight does not exist
 * - Flight status does not allow booking (e.g., DEPARTED, ARRIVED)
 * - No seats available
 */
public class FlightNotBookableException extends RuntimeException {

    private final String flightNumber;
    private final String reason;

    public FlightNotBookableException(String flightNumber, String reason) {
        super("Flight " + flightNumber + " is not bookable: " + reason);
        this.flightNumber = flightNumber;
        this.reason = reason;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getReason() {
        return reason;
    }
}
