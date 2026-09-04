package com.anadoluair.flight.bookingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when check-in is not allowed due to time restrictions.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CheckInNotAllowedException extends RuntimeException {

    public CheckInNotAllowedException(String pnr, String reason) {
        super(String.format("Check-in not allowed for booking %s: %s", pnr, reason));
    }
}
