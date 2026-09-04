package com.anadoluair.flight.bookingservice.exception;

import com.anadoluair.flight.bookingservice.entity.Booking.BookingStatus;

/**
 * Exception thrown when a booking operation is invalid due to current state.
 */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(String pnr, BookingStatus currentStatus, String operation) {
        super(String.format("Cannot %s booking %s: current status is %s",
                operation, pnr, currentStatus));
    }

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
