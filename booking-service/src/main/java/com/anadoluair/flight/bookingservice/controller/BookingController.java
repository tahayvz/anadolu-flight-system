package com.anadoluair.flight.bookingservice.controller;

import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.dto.BookingResponse;
import com.anadoluair.flight.bookingservice.exception.ErrorResponse;
import com.anadoluair.flight.bookingservice.saga.BookingSagaOrchestrator;
import com.anadoluair.flight.bookingservice.saga.BookingSagaState;
import com.anadoluair.flight.bookingservice.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for booking operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Booking API", description = "Flight booking management operations")
public class BookingController {

    private final BookingService bookingService;
    private final BookingSagaOrchestrator sagaOrchestrator;

    @Operation(
            summary = "Create a new booking",
            description = "Creates a new flight booking with passenger details. Returns PNR (booking reference)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        log.info("POST /api/bookings - Creating booking for flight: {}", request.getFlightNumber());

        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get all bookings",
            description = "Retrieves all bookings in the system"
    )
    @ApiResponse(responseCode = "200", description = "List of all bookings")
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        log.info("GET /api/bookings - Retrieving all bookings");

        List<BookingResponse> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    @Operation(
            summary = "Get booking by PNR",
            description = "Retrieves a specific booking using its PNR (Passenger Name Record)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{pnr}")
    public ResponseEntity<BookingResponse> getBookingByPNR(
            @Parameter(description = "6-character PNR code", example = "ABC123")
            @PathVariable String pnr) {
        log.info("GET /api/bookings/{} - Retrieving booking", pnr);

        BookingResponse response = bookingService.getBookingByPNR(pnr.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get bookings for a flight",
            description = "Retrieves all bookings for a specific flight on a given date"
    )
    @ApiResponse(responseCode = "200", description = "List of bookings for the flight")
    @GetMapping("/flight/{flightNumber}")
    public ResponseEntity<List<BookingResponse>> getBookingsForFlight(
            @Parameter(description = "Flight number", example = "TK1")
            @PathVariable String flightNumber,
            @Parameter(description = "Flight date", example = "2024-06-15")
            @RequestParam LocalDate date) {
        log.info("GET /api/bookings/flight/{} on {} - Retrieving bookings",
                flightNumber, date);

        List<BookingResponse> bookings = bookingService.getBookingsForFlight(
                flightNumber.toUpperCase(), date);
        return ResponseEntity.ok(bookings);
    }

    @Operation(
            summary = "Cancel a booking",
            description = "Cancels an existing booking. Cannot cancel already cancelled bookings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "409", description = "Booking cannot be cancelled (invalid state)")
    })
    @DeleteMapping("/{pnr}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @Parameter(description = "6-character PNR code", example = "ABC123")
            @PathVariable String pnr) {
        log.info("DELETE /api/bookings/{} - Cancelling booking", pnr);

        BookingResponse response = bookingService.cancelBooking(pnr.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Check-in for a booking",
            description = "Performs online check-in for a booking. Assigns seats to passengers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in completed successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "409", description = "Booking cannot be checked in (invalid state)")
    })
    @PostMapping("/{pnr}/checkin")
    public ResponseEntity<BookingResponse> checkIn(
            @Parameter(description = "6-character PNR code", example = "ABC123")
            @PathVariable String pnr) {
        log.info("POST /api/bookings/{}/checkin - Processing check-in", pnr);

        BookingResponse response = bookingService.checkIn(pnr.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Health check", description = "Check if the service is running")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "booking-service",
                "database", "H2 (in-memory)"
        ));
    }

    // ==================== Saga-Based Booking (Async with Rollback) ====================

    @Operation(
            summary = "Create booking via Saga (async)",
            description = "Creates a booking using the Saga pattern with Kafka. Returns saga ID to track progress. " +
                    "Automatically rolls back on failure."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Saga started - booking in progress"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping("/saga")
    public ResponseEntity<Map<String, Object>> createBookingWithSaga(@Valid @RequestBody BookingRequest request) {
        log.info("POST /api/bookings/saga - Starting booking saga for flight: {}", request.getFlightNumber());

        BookingSagaState saga = sagaOrchestrator.startSaga(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "sagaId", saga.getSagaId(),
                "status", saga.getStatus().name(),
                "currentStep", saga.getCurrentStep().name(),
                "message", "Booking saga started. Use /api/sagas/{sagaId} to track progress.",
                "trackingUrl", "/api/sagas/" + saga.getSagaId()
        ));
    }
}
