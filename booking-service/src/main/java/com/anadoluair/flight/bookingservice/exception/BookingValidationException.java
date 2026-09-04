package com.anadoluair.flight.bookingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when booking validation fails.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BookingValidationException extends RuntimeException {

    public BookingValidationException(String message) {
        super(message);
    }

    public static BookingValidationException tooCloseToDeperture(int minHours) {
        return new BookingValidationException(
            String.format("Booking must be made at least %d hours before departure", minHours));
    }

    public static BookingValidationException tooManyPassengers(int max) {
        return new BookingValidationException(
            String.format("Maximum %d passengers allowed per booking", max));
    }

    public static BookingValidationException infantRequiresAdult() {
        return new BookingValidationException(
            "Infant passengers must be accompanied by an adult");
    }

    public static BookingValidationException tooManyInfantsPerAdult(int max) {
        return new BookingValidationException(
            String.format("Maximum %d infant(s) allowed per adult passenger", max));
    }

    public static BookingValidationException invalidPassengerAge(String passengerName, String expectedType, int age) {
        return new BookingValidationException(
            String.format("Passenger %s age (%d) does not match type %s", passengerName, age, expectedType));
    }
}
