package com.anadoluair.flight.flightopsservice.config;

import com.anadoluair.flight.events.FlightEvent.FlightStatus;
import com.anadoluair.flight.events.FlightState;
import com.anadoluair.flight.flightopsservice.repository.FlightStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Initializes realistic flight data on application startup.
 * Creates flights with staggered departure times for demo purposes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightDataInitializer implements CommandLineRunner {

    private final FlightStateRepository flightStateRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing flight data...");

        LocalDateTime now = LocalDateTime.now();

        // Define realistic outbound routes with durations
        // IST = İstanbul Havalimanı (Istanbul Airport)
        List<FlightRoute> outboundRoutes = List.of(
                new FlightRoute("TK1", "IST", "JFK", 11, "Boeing 777-300ER", 350),
                new FlightRoute("TK7", "IST", "LHR", 4, "Airbus A350-900", 325),
                new FlightRoute("TK11", "IST", "CDG", 3, "Airbus A321neo", 182),
                new FlightRoute("TK77", "IST", "FRA", 3, "Boeing 737-900ER", 175),
                new FlightRoute("TK123", "IST", "DXB", 4, "Airbus A330-300", 289),
                new FlightRoute("TK1984", "IST", "SIN", 10, "Boeing 787-9", 270),
                new FlightRoute("TK2001", "IST", "ESB", 1, "Airbus A320neo", 180),
                new FlightRoute("TK2010", "IST", "AYT", 1, "Boeing 737-800", 189),
                new FlightRoute("TK2020", "IST", "ADB", 1, "Airbus A321neo", 182)
        );

        // Define inbound routes so "Arrivals" board has live data as well
        List<FlightRoute> inboundRoutes = List.of(
                new FlightRoute("TK2", "JFK", "IST", 11, "Boeing 777-300ER", 350),
                new FlightRoute("TK8", "LHR", "IST", 4, "Airbus A350-900", 325),
                new FlightRoute("TK12", "CDG", "IST", 3, "Airbus A321neo", 182),
                new FlightRoute("TK78", "FRA", "IST", 3, "Boeing 737-900ER", 175),
                new FlightRoute("TK124", "DXB", "IST", 4, "Airbus A330-300", 289),
                new FlightRoute("TK1985", "SIN", "IST", 10, "Boeing 787-9", 270),
                new FlightRoute("TK2002", "ESB", "IST", 1, "Airbus A320neo", 180),
                new FlightRoute("TK2011", "AYT", "IST", 1, "Boeing 737-800", 189),
                new FlightRoute("TK2021", "ADB", "IST", 1, "Airbus A321neo", 182)
        );

        // Create outbound flights with staggered departure times
        // First flight departs in 2 hours, then every 30 minutes
        // This ensures flights stay SCHEDULED/BOARDING for simulation purposes
        int offsetMinutes = 120;

        for (FlightRoute route : outboundRoutes) {
            LocalDateTime departure = now.plusMinutes(offsetMinutes);
            LocalDateTime arrival = departure.plusHours(route.durationHours);

            FlightState flight = FlightState.builder()
                    .flightNumber(route.flightNumber)
                    .airline("Anadolu Air")
                    .origin(route.origin)
                    .destination(route.destination)
                    .status(FlightStatus.SCHEDULED)
                    .terminal("International")
                    .gate(null)  // Gate assigned when BOARDING starts
                    .scheduledDeparture(departure)
                    .scheduledArrival(arrival)
                    .estimatedDeparture(departure)
                    .estimatedArrival(arrival)
                    .actualDeparture(null)
                    .actualArrival(null)
                    .aircraft(route.aircraft)
                    .totalSeats(route.seats)
                    .bookedSeats(0)
                    .lastUpdated(now)
                    .build();

            flightStateRepository.save(flight);

            log.info("Initialized flight: {} ({} -> {}) departing at {} with {} seats",
                    route.flightNumber,
                    route.origin,
                    route.destination,
                    departure.toLocalTime(),
                    route.seats);

            offsetMinutes += 30; // Stagger departures by 30 minutes
        }

        // Create inbound flights that are already en route to IST
        // First inbound arrival in ~30 minutes, then every 30 minutes
        int inboundArrivalOffsetMinutes = 30;

        for (FlightRoute route : inboundRoutes) {
            LocalDateTime arrival = now.plusMinutes(inboundArrivalOffsetMinutes);
            LocalDateTime departure = arrival.minusHours(route.durationHours);

            FlightState inboundFlight = FlightState.builder()
                    .flightNumber(route.flightNumber)
                    .airline("Anadolu Air")
                    .origin(route.origin)
                    .destination(route.destination)
                    .status(FlightStatus.IN_FLIGHT)
                    .terminal("International")
                    .gate(null)
                    .scheduledDeparture(departure)
                    .scheduledArrival(arrival)
                    .estimatedDeparture(departure)
                    .estimatedArrival(arrival)
                    .actualDeparture(null)
                    .actualArrival(null)
                    .aircraft(route.aircraft)
                    .totalSeats(route.seats)
                    .bookedSeats(0)
                    .lastUpdated(now)
                    .build();

            flightStateRepository.save(inboundFlight);

            log.info("Initialized inbound flight: {} ({} -> {}) arriving at {} with {} seats",
                    route.flightNumber,
                    route.origin,
                    route.destination,
                    arrival.toLocalTime(),
                    route.seats);

            inboundArrivalOffsetMinutes += 30;
        }

        log.info("Flight data initialization complete. {} flights ready.",
                outboundRoutes.size() + inboundRoutes.size());
    }

    /**
     * Flight route definition.
     */
    private record FlightRoute(
            String flightNumber,
            String origin,
            String destination,
            int durationHours,
            String aircraft,
            int seats
    ) {}
}
