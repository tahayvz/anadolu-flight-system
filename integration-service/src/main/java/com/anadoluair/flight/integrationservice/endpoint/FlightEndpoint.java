package com.anadoluair.flight.integrationservice.endpoint;

import com.anadoluair.flight.integrationservice.service.FlightService;
import com.anadoluair.integration.generated.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * SOAP Endpoint for Flight operations.
 * Handles incoming SOAP requests and delegates to FlightService.
 *
 * WSDL: http://localhost:8081/ws/flights.wsdl
 *
 * Example SOAP Request for SearchFlights:
 * <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
 *                   xmlns:soap="http://anadoluair.example/flight/soap">
 *    <soapenv:Body>
 *       <soap:SearchFlightsRequest>
 *          <soap:origin>IST</soap:origin>
 *          <soap:destination>JFK</soap:destination>
 *          <soap:departureDate>2024-06-15</soap:departureDate>
 *          <soap:passengerCount>1</soap:passengerCount>
 *       </soap:SearchFlightsRequest>
 *    </soapenv:Body>
 * </soapenv:Envelope>
 */
@Slf4j
@Endpoint
@RequiredArgsConstructor
public class FlightEndpoint {

    private static final String NAMESPACE_URI = "http://anadoluair.example/flight/soap";

    private final FlightService flightService;

    /**
     * SOAP endpoint for searching available flights.
     *
     * @param request Search criteria including origin, destination, date
     * @return List of matching flights with availability and pricing
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "SearchFlightsRequest")
    @ResponsePayload
    public SearchFlightsResponse searchFlights(@RequestPayload SearchFlightsRequest request) {
        log.info("SOAP Request: SearchFlights - {} to {} on {}",
                request.getOrigin(),
                request.getDestination(),
                request.getDepartureDate());

        return flightService.searchFlights(request);
    }

    /**
     * SOAP endpoint for getting real-time flight status.
     *
     * @param request Flight number and date
     * @return Current flight status with gate and terminal info
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "GetFlightStatusRequest")
    @ResponsePayload
    public GetFlightStatusResponse getFlightStatus(@RequestPayload GetFlightStatusRequest request) {
        log.info("SOAP Request: GetFlightStatus - Flight {}",
                request.getFlightNumber());

        return flightService.getFlightStatus(request);
    }

    /**
     * SOAP endpoint for creating a new booking.
     *
     * @param request Booking details including flight, passengers, contact info
     * @return Booking confirmation with PNR and status
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "CreateBookingRequest")
    @ResponsePayload
    public CreateBookingResponse createBooking(@RequestPayload CreateBookingRequest request) {
        log.info("SOAP Request: CreateBooking - Flight {} with {} passengers",
                request.getFlightNumber(),
                request.getPassengers().size());

        return flightService.createBooking(request);
    }
}
