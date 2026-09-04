package com.anadoluair.flight.integrationservice.service;

import com.anadoluair.flight.events.FlightEvent;
import com.anadoluair.flight.integrationservice.kafka.FlightEventProducer;
import com.anadoluair.integration.generated.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business logic service for flight operations.
 * Handles SOAP request processing and publishes events to Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightEventProducer eventProducer;

    /**
     * Search for available flights based on criteria.
     * In a real system, this would query an external reservation system.
     */
    public SearchFlightsResponse searchFlights(SearchFlightsRequest request) {
        log.info("Searching flights: {} -> {} on {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        // Simulate flight search results (in production, call external API/database)
        List<Flight> flights = generateMockFlights(
                request.getOrigin(),
                request.getDestination(),
                request.getDepartureDate()
        );

        SearchFlightsResponse response = new SearchFlightsResponse();
        response.getFlights().addAll(flights);
        response.setTotalResults(flights.size());

        log.info("Found {} flights", flights.size());
        return response;
    }

    /**
     * Get real-time flight status.
     * Publishes a status check event to Kafka for tracking.
     */
    public GetFlightStatusResponse getFlightStatus(GetFlightStatusRequest request) {
        log.info("Getting status for flight: {} on {}",
                request.getFlightNumber(), request.getFlightDate());

        // Simulate flight status (in production, query real-time system)
        GetFlightStatusResponse response = new GetFlightStatusResponse();

        Flight flight = new Flight();
        flight.setFlightNumber(request.getFlightNumber());
        flight.setAirline("ZZ");
        flight.setOrigin("IST");
        flight.setDestination("JFK");

        response.setFlight(flight);
        response.setStatus(FlightStatus.SCHEDULED);
        response.setGate("A12");
        response.setTerminal("International");

        // Publish status check event
        publishFlightStatusEvent(request.getFlightNumber(), FlightStatus.SCHEDULED);

        return response;
    }

    /**
     * Create a new booking.
     * Publishes booking creation event to Kafka.
     */
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        log.info("Creating booking for flight: {} with {} passengers",
                request.getFlightNumber(), request.getPassengers().size());

        // Generate booking reference (PNR)
        String pnr = generatePNR();

        CreateBookingResponse response = new CreateBookingResponse();
        response.setBookingReference(pnr);
        response.setStatus(BookingStatus.CONFIRMED);
        response.setTotalAmount(calculateTotalAmount(request.getPassengers().size()));
        response.setCurrency("TRY");

        // Publish flight event for the booking
        publishFlightCreatedEvent(request.getFlightNumber());

        log.info("Booking created: PNR={}", pnr);
        return response;
    }

    /**
     * Publish a flight status event to Kafka.
     */
    private void publishFlightStatusEvent(String flightNumber, FlightStatus status) {
        FlightEvent event = FlightEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(FlightEvent.EventType.STATUS_CHANGED)
                .timestamp(LocalDateTime.now())
                .flightData(FlightEvent.FlightData.builder()
                        .flightNumber(flightNumber)
                        .airline("ZZ")
                        .status(mapToEventStatus(status))
                        .build())
                .build();

        eventProducer.publishStatusUpdate(event);
    }

    /**
     * Publish a flight created event to Kafka.
     */
    private void publishFlightCreatedEvent(String flightNumber) {
        FlightEvent event = FlightEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(FlightEvent.EventType.FLIGHT_CREATED)
                .timestamp(LocalDateTime.now())
                .flightData(FlightEvent.FlightData.builder()
                        .flightNumber(flightNumber)
                        .airline("ZZ")
                        .origin("IST")
                        .destination("JFK")
                        .status(FlightEvent.FlightStatus.SCHEDULED)
                        .build())
                .build();

        eventProducer.publishFlightEvent(event);
    }

    /**
     * Map SOAP FlightStatus to Event FlightStatus.
     */
    private FlightEvent.FlightStatus mapToEventStatus(FlightStatus soapStatus) {
        return switch (soapStatus) {
            case SCHEDULED -> FlightEvent.FlightStatus.SCHEDULED;
            case BOARDING -> FlightEvent.FlightStatus.BOARDING;
            case DEPARTED -> FlightEvent.FlightStatus.DEPARTED;
            case IN_FLIGHT -> FlightEvent.FlightStatus.IN_FLIGHT;
            case LANDED -> FlightEvent.FlightStatus.LANDED;
            case ARRIVED -> FlightEvent.FlightStatus.ARRIVED;
            case DELAYED -> FlightEvent.FlightStatus.DELAYED;
            case CANCELLED -> FlightEvent.FlightStatus.CANCELLED;
        };
    }

    /**
     * Generate mock flight data for demo purposes.
     */
    private List<Flight> generateMockFlights(String origin, String destination, XMLGregorianCalendar date) {
        List<Flight> flights = new ArrayList<>();

        // Generate 3 sample flights
        String[] times = {"08:00", "14:30", "20:45"};
        String[] flightNumbers = {"ZZ1", "ZZ7", "ZZ11"};

        for (int i = 0; i < 3; i++) {
            Flight flight = new Flight();
            flight.setFlightNumber(flightNumbers[i]);
            flight.setAirline("Anadolu Air");
            flight.setOrigin(origin);
            flight.setDestination(destination);
            flight.setAircraft("Boeing 777-300ER");
            flight.setPrice(BigDecimal.valueOf(1500 + (i * 500)));
            flight.setCurrency("TRY");
            flight.setAvailableSeats(150 - (i * 30));
            flights.add(flight);
        }

        return flights;
    }

    /**
     * Generate a random 6-character PNR (Passenger Name Record).
     */
    private String generatePNR() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder pnr = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            pnr.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return pnr.toString();
    }

    /**
     * Calculate total booking amount.
     */
    private BigDecimal calculateTotalAmount(int passengerCount) {
        // Base price per passenger
        BigDecimal basePrice = BigDecimal.valueOf(2500);
        return basePrice.multiply(BigDecimal.valueOf(passengerCount));
    }
}
