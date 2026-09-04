package com.anadoluair.flight.flightopsservice.scheduler;

import com.anadoluair.flight.events.FlightEvent.FlightStatus;
import com.anadoluair.flight.events.FlightState;
import com.anadoluair.flight.flightopsservice.dto.FlightStatusDTO;
import com.anadoluair.flight.flightopsservice.repository.FlightStateRepository;
import com.anadoluair.flight.flightopsservice.websocket.FlightWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Scheduler for realistic flight lifecycle progression.
 * Advances flights through their lifecycle based on actual time.
 *
 * Lifecycle: SCHEDULED -> BOARDING -> DEPARTED -> IN_FLIGHT -> LANDED -> ARRIVED
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FlightStatusScheduler {

    private final FlightWebSocketHandler webSocketHandler;
    private final FlightStateRepository flightStateRepository;
    private final Random random = new Random();

    private static final List<String> GATES = List.of(
            "A1", "A2", "A3", "A12", "B3", "B7", "C2", "C15", "D4", "D8"
    );

    /**
     * Progress flights through their lifecycle every 10 seconds.
     * Checks each flight and advances status based on current time.
     */
    @Scheduled(fixedRate = 10000)
    public void progressFlightLifecycle() {
        LocalDateTime now = LocalDateTime.now();

        for (FlightState flight : flightStateRepository.findAll()) {
            FlightStatus previousStatus = flight.getStatus();
            FlightStatus newStatus = calculateNextStatus(flight, now);

            if (newStatus != previousStatus) {
                updateFlightStatus(flight, newStatus, now);
                broadcastUpdate(flight);

                log.info("Flight {} transitioned: {} -> {} (gate: {})",
                        flight.getFlightNumber(),
                        previousStatus,
                        newStatus,
                        flight.getGate());
            }
        }
    }

    /**
     * Calculate the next status based on current time and flight schedule.
     *
     * Timeline:
     * - 45 min before departure: BOARDING starts
     * - At departure time: DEPARTED
     * - 15 min after departure: IN_FLIGHT (airborne)
     * - 20 min before arrival: LANDED
     * - At arrival time: ARRIVED
     */
    private FlightStatus calculateNextStatus(FlightState flight, LocalDateTime now) {
        FlightStatus currentStatus = flight.getStatus();

        // Terminal states - don't progress further
        if (currentStatus == FlightStatus.ARRIVED || currentStatus == FlightStatus.CANCELLED) {
            return currentStatus;
        }

        LocalDateTime departure = flight.getScheduledDeparture();
        LocalDateTime arrival = flight.getScheduledArrival();

        // Calculate time thresholds
        LocalDateTime boardingStart = departure.minusMinutes(FlightState.BOARDING_DURATION);
        LocalDateTime taxiComplete = departure.plusMinutes(FlightState.TAXI_DURATION);
        LocalDateTime landingTime = arrival.minusMinutes(FlightState.ARRIVAL_PROCESSING);

        // Time-based status progression (order matters - check from latest to earliest)
        if (now.isAfter(arrival) || now.isEqual(arrival)) {
            return FlightStatus.ARRIVED;
        }
        if (now.isAfter(landingTime)) {
            return FlightStatus.LANDED;
        }
        if (now.isAfter(taxiComplete)) {
            return FlightStatus.IN_FLIGHT;
        }
        if (now.isAfter(departure) || now.isEqual(departure)) {
            return FlightStatus.DEPARTED;
        }
        if (now.isAfter(boardingStart)) {
            return FlightStatus.BOARDING;
        }

        return FlightStatus.SCHEDULED;
    }

    /**
     * Update flight state based on new status.
     * Handles gate assignment, actual times, etc.
     */
    private void updateFlightStatus(FlightState flight, FlightStatus newStatus, LocalDateTime now) {
        flight.setStatus(newStatus);
        flight.setLastUpdated(now);

        switch (newStatus) {
            case BOARDING -> {
                // Assign gate when boarding starts
                if (flight.getGate() == null) {
                    flight.setGate(getRandomGate());
                }
            }
            case DEPARTED -> {
                // Record actual departure time
                flight.setActualDeparture(now);

                // Recalculate estimated arrival based on actual departure
                long flightDurationMinutes = Duration.between(
                        flight.getScheduledDeparture(),
                        flight.getScheduledArrival()
                ).toMinutes();
                flight.setEstimatedArrival(now.plusMinutes(flightDurationMinutes));
            }
            case IN_FLIGHT -> {
                // Clear departure gate after takeoff
                flight.setGate(null);
            }
            case LANDED -> {
                // Assign arrival gate
                flight.setGate(getRandomGate());
            }
            case ARRIVED -> {
                // Record actual arrival time
                flight.setActualArrival(now);
            }
        }

        flightStateRepository.save(flight);
    }

    /**
     * Broadcast flight update via WebSocket.
     */
    private void broadcastUpdate(FlightState flight) {
        FlightStatusDTO dto = mapToDTO(flight);
        webSocketHandler.broadcastFlightUpdate(dto);
        webSocketHandler.sendFlightUpdate(flight.getFlightNumber(), dto);
    }

    /**
     * Map FlightState to FlightStatusDTO for WebSocket.
     */
    private FlightStatusDTO mapToDTO(FlightState flight) {
        return FlightStatusDTO.builder()
                .flightNumber(flight.getFlightNumber())
                .airline(flight.getAirline())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .status(flight.getStatus().name())
                .gate(flight.getGate())
                .terminal(flight.getTerminal())
                .scheduledDeparture(flight.getScheduledDeparture())
                .estimatedDeparture(flight.getEstimatedDeparture())
                .scheduledArrival(flight.getScheduledArrival())
                .estimatedArrival(flight.getEstimatedArrival())
                .aircraft(flight.getAircraft())
                .remarks(getStatusRemark(flight.getStatus()))
                .lastUpdated(flight.getLastUpdated())
                .build();
    }

    /**
     * Get appropriate remark for flight status.
     */
    private String getStatusRemark(FlightStatus status) {
        return switch (status) {
            case SCHEDULED -> "On schedule";
            case BOARDING -> "Now boarding";
            case DEPARTED -> "Departed";
            case IN_FLIGHT -> "In flight";
            case LANDED -> "Landed";
            case ARRIVED -> "Arrived at gate";
            case DELAYED -> "Delayed";
            case CANCELLED -> "Cancelled";
        };
    }

    /**
     * Get a random gate assignment.
     */
    private String getRandomGate() {
        return GATES.get(random.nextInt(GATES.size()));
    }

    /**
     * Broadcast status board summary every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void broadcastStatusBoard() {
        List<FlightState> allFlights = flightStateRepository.findAll();

        long scheduled = allFlights.stream()
                .filter(f -> f.getStatus() == FlightStatus.SCHEDULED)
                .count();
        long boarding = allFlights.stream()
                .filter(f -> f.getStatus() == FlightStatus.BOARDING)
                .count();
        long inFlight = allFlights.stream()
                .filter(f -> f.getStatus() == FlightStatus.DEPARTED || f.getStatus() == FlightStatus.IN_FLIGHT)
                .count();
        long arrived = allFlights.stream()
                .filter(f -> f.getStatus() == FlightStatus.LANDED || f.getStatus() == FlightStatus.ARRIVED)
                .count();

        var summary = new StatusBoardSummary(
                LocalDateTime.now(),
                (int) (scheduled + boarding),
                (int) arrived,
                (int) inFlight,
                0  // No delays in basic simulation
        );

        webSocketHandler.broadcastStatusBoardUpdate(summary);

        log.debug("Status board: departures={}, arrivals={}, inFlight={}",
                scheduled + boarding, arrived, inFlight);
    }

    /**
     * Status board summary record.
     */
    public record StatusBoardSummary(
            LocalDateTime timestamp,
            int totalDepartures,
            int totalArrivals,
            int inFlight,
            int delayedFlights
    ) {}
}
