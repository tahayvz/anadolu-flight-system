package com.anadoluair.flight.bookingservice.exception;

/**
 * Exception thrown when a booking is not found.
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String pnr) {
        super("Booking not found with PNR: " + pnr);
    }

    public BookingNotFoundException(Long id) {
        super("Booking not found with ID: " + id);
    }
}
