package com.anadoluair.flight.bookingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.dto.BookingResponse;
import com.anadoluair.flight.bookingservice.kafka.BookingEventProducer;
import com.anadoluair.flight.events.BookingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.anadoluair.flight.bookingservice.AbstractBookingTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BookingController.
 */
@AutoConfigureMockMvc
class BookingControllerTest extends AbstractBookingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingEventProducer bookingEventProducer;

    @Test
    @DisplayName("POST /api/bookings - should create booking successfully")
    void createBooking_Success() throws Exception {
        // Given
        doNothing().when(bookingEventProducer).publishBookingEvent(any(BookingEvent.class));

        BookingRequest request = BookingRequest.builder()
                .flightNumber("ZZ1")
                .flightDate(LocalDate.now().plusDays(7))
                .contactEmail("test@example.com")
                .contactPhone("+905551234567")
                .passengers(List.of(
                        BookingRequest.PassengerDTO.builder()
                                .firstName("John")
                                .lastName("Doe")
                                .dateOfBirth(LocalDate.of(1990, 5, 15))
                                .passengerType("ADULT")
                                .build()
                ))
                .build();

        // When & Then
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingReference").exists())
                .andExpect(jsonPath("$.bookingReference").isString())
                .andExpect(jsonPath("$.flightNumber").value("ZZ1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.passengers").isArray())
                .andExpect(jsonPath("$.passengers[0].firstName").value("John"));
    }

    @Test
    @DisplayName("POST /api/bookings - should fail with invalid request")
    void createBooking_ValidationError() throws Exception {
        // Given - Invalid request (missing required fields)
        BookingRequest request = BookingRequest.builder()
                .flightNumber("")  // Invalid - empty
                .flightDate(LocalDate.now().minusDays(1))  // Invalid - past date
                .passengers(List.of())  // Invalid - empty
                .build();

        // When & Then
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("GET /api/bookings - should return all bookings")
    void getAllBookings_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/bookings/{pnr} - should return 404 for non-existent PNR")
    void getBookingByPNR_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/bookings/XXXXXX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Booking Not Found"));
    }

    @Test
    @DisplayName("GET /api/bookings/health - should return UP status")
    void healthCheck_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/bookings/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("booking-service"));
    }

    @Test
    @DisplayName("Full booking lifecycle - create and retrieve (check-in depends on 24h rule)")
    void bookingLifecycle() throws Exception {
        // Mock Kafka producer
        doNothing().when(bookingEventProducer).publishBookingEvent(any(BookingEvent.class));

        // 1. Create booking for a flight 7 days from now
        BookingRequest request = BookingRequest.builder()
                .flightNumber("ZZ7")
                .flightDate(LocalDate.now().plusDays(7))
                .contactEmail("lifecycle@test.com")
                .contactPhone("+905559876543")
                .passengers(List.of(
                        BookingRequest.PassengerDTO.builder()
                                .firstName("Jane")
                                .lastName("Smith")
                                .dateOfBirth(LocalDate.of(1985, 8, 20))
                                .passengerType("ADULT")
                                .build()
                ))
                .build();

        String createResponse = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        BookingResponse booking = objectMapper.readValue(createResponse, BookingResponse.class);
        String pnr = booking.getBookingReference();

        // 2. Retrieve booking
        mockMvc.perform(get("/api/bookings/" + pnr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(pnr))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 3. Try to check-in (should fail - flight is more than 24h away)
        // This validates the 24h check-in rule is working correctly
        mockMvc.perform(post("/api/bookings/" + pnr + "/checkin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Check-in Not Allowed"));
    }

    @Test
    @DisplayName("Check-in should fail for flight too far in the future (24h rule)")
    void checkIn_TooEarly() throws Exception {
        // Mock Kafka producer
        doNothing().when(bookingEventProducer).publishBookingEvent(any(BookingEvent.class));

        // Create booking for flight in 5 days
        BookingRequest request = BookingRequest.builder()
                .flightNumber("ZZ1951")
                .flightDate(LocalDate.now().plusDays(5))
                .contactEmail("early@test.com")
                .contactPhone("+905551112233")
                .passengers(List.of(
                        BookingRequest.PassengerDTO.builder()
                                .firstName("Early")
                                .lastName("Checkin")
                                .dateOfBirth(LocalDate.of(1992, 3, 10))
                                .passengerType("ADULT")
                                .build()
                ))
                .build();

        String createResponse = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        BookingResponse booking = objectMapper.readValue(createResponse, BookingResponse.class);

        // Try check-in - should fail with 24h rule
        mockMvc.perform(post("/api/bookings/" + booking.getBookingReference() + "/checkin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
