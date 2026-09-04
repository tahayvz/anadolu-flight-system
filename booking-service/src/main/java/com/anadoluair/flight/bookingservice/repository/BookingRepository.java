package com.anadoluair.flight.bookingservice.repository;

import com.anadoluair.flight.bookingservice.entity.Booking;
import com.anadoluair.flight.bookingservice.entity.Booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for Booking entity.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Find booking by PNR (booking reference).
     */
    Optional<Booking> findByBookingReference(String bookingReference);

    /**
     * Find all bookings for a specific flight.
     */
    List<Booking> findByFlightNumberAndFlightDate(String flightNumber, LocalDate flightDate);

    /**
     * Find bookings by status.
     */
    List<Booking> findByStatus(BookingStatus status);

    /**
     * Find bookings by contact email.
     */
    List<Booking> findByContactEmail(String contactEmail);

    /**
     * Count bookings for a specific flight.
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.flightNumber = :flightNumber AND b.flightDate = :flightDate")
    long countBookingsForFlight(@Param("flightNumber") String flightNumber,
                                 @Param("flightDate") LocalDate flightDate);

    /**
     * Find upcoming bookings (flights in the future).
     */
    @Query("SELECT b FROM Booking b WHERE b.flightDate >= :today ORDER BY b.flightDate ASC")
    List<Booking> findUpcomingBookings(@Param("today") LocalDate today);

    /**
     * Check if PNR exists.
     */
    boolean existsByBookingReference(String bookingReference);
}
