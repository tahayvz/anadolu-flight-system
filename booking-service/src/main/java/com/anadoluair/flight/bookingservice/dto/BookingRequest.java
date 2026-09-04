package com.anadoluair.flight.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for creating a new booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotBlank(message = "Flight number is required")
    @Pattern(regexp = "[A-Z]{2}\\d{1,4}", message = "Invalid flight number format")
    private String flightNumber;

    @NotNull(message = "Flight date is required")
    @FutureOrPresent(message = "Flight date must be today or in the future")
    private LocalDate flightDate;

    @NotEmpty(message = "At least one passenger is required")
    @Size(max = 9, message = "Maximum 9 passengers per booking")
    @Valid
    private List<PassengerDTO> passengers;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerDTO {

        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        private LocalDate dateOfBirth;

        @NotNull(message = "Passenger type is required")
        private String passengerType;

        private String nationality;
        private String passportNumber;
    }
}
