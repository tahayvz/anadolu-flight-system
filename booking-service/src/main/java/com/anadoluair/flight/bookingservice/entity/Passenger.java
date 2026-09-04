package com.anadoluair.flight.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * JPA Entity for passengers within a booking.
 */
@Entity
@Table(name = "passengers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    @ToString.Exclude
    private Booking booking;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "passenger_type")
    private PassengerType passengerType;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "passport_number")
    private String passportNumber;

    @Column(name = "seat_number")
    private String seatNumber;

    @Column(name = "ticket_number")
    private String ticketNumber;

    public enum PassengerType {
        ADULT,
        CHILD,
        INFANT
    }
}
