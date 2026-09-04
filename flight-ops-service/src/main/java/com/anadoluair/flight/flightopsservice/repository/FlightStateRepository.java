package com.anadoluair.flight.flightopsservice.repository;

import com.anadoluair.flight.events.FlightEvent.FlightStatus;
import com.anadoluair.flight.events.FlightState;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for flight state management.
 * Uses ConcurrentHashMap for thread-safe operations.
 */
@Repository
public class FlightStateRepository {

    private final Map<String, FlightState> flights = new ConcurrentHashMap<>();

    /**
     * Save or update a flight state.
     */
    public void save(FlightState flight) {
        flights.put(flight.getFlightNumber(), flight);
    }

    /**
     * Find a flight by its flight number.
     */
    public Optional<FlightState> findByFlightNumber(String flightNumber) {
        return Optional.ofNullable(flights.get(flightNumber.toUpperCase()));
    }

    /**
     * Get all flights.
     */
    public List<FlightState> findAll() {
        return new ArrayList<>(flights.values());
    }

    /**
     * Find flights by status.
     */
    public List<FlightState> findByStatus(FlightStatus status) {
        return flights.values().stream()
                .filter(f -> f.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Find flights that are bookable (SCHEDULED or BOARDING).
     */
    public List<FlightState> findBookableFlights() {
        return flights.values().stream()
                .filter(FlightState::isBookable)
                .collect(Collectors.toList());
    }

    /**
     * Check if a flight exists.
     */
    public boolean exists(String flightNumber) {
        return flights.containsKey(flightNumber.toUpperCase());
    }

    /**
     * Increment booked seats for a flight.
     */
    public synchronized void incrementBookedSeats(String flightNumber, int count) {
        FlightState flight = flights.get(flightNumber.toUpperCase());
        if (flight != null) {
            flight.setBookedSeats(flight.getBookedSeats() + count);
        }
    }

    /**
     * Get available seats for a flight.
     */
    public int getAvailableSeats(String flightNumber) {
        FlightState flight = flights.get(flightNumber.toUpperCase());
        return flight != null ? flight.getAvailableSeats() : 0;
    }

    /**
     * Get total flight count.
     */
    public int count() {
        return flights.size();
    }

    /**
     * Remove a flight (useful for cleanup).
     */
    public void delete(String flightNumber) {
        flights.remove(flightNumber.toUpperCase());
    }

    /**
     * Clear all flights.
     */
    public void clear() {
        flights.clear();
    }
}
