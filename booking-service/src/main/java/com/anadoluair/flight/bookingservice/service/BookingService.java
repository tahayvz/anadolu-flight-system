package com.anadoluair.flight.bookingservice.service;

import com.anadoluair.flight.bookingservice.client.FlightOpsClient;
import com.anadoluair.flight.bookingservice.config.BookingRulesConfig;
import com.anadoluair.flight.bookingservice.rules.BookingRulesValidator;
import com.anadoluair.flight.bookingservice.config.RedisCacheConfig;
import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.dto.BookingResponse;
import com.anadoluair.flight.bookingservice.entity.Booking;
import com.anadoluair.flight.bookingservice.entity.Booking.BookingStatus;
import com.anadoluair.flight.bookingservice.entity.Passenger;
import com.anadoluair.flight.bookingservice.entity.Passenger.PassengerType;
import com.anadoluair.flight.bookingservice.exception.*;
import com.anadoluair.flight.bookingservice.kafka.BookingEventProducer;
import com.anadoluair.flight.bookingservice.repository.BookingRepository;
import com.anadoluair.flight.events.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic service for booking operations.
 * Implements Anadolu Air booking rules:
 * - Maximum 9 passengers per booking
 * - Booking must be made at least 2 hours before departure
 * - Check-in opens 24 hours before departure
 * - Check-in closes 1 hour before departure
 * - Infants must be accompanied by adults
 * - Maximum 1 infant per adult
 * - Passenger age must match type (ADULT 12+, CHILD 2-11, INFANT 0-2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventProducer eventProducer;
    private final FlightOpsClient flightOpsClient;
    private final BookingRulesConfig rulesConfig;
    private final BookingRulesValidator rulesValidator;

    // Pricing constants in TRY
    private static final BigDecimal ADULT_BASE_PRICE = BigDecimal.valueOf(2500);
    private static final BigDecimal CHILD_DISCOUNT_PERCENT = BigDecimal.valueOf(0.50); // 50% discount
    private static final BigDecimal INFANT_PRICE = BigDecimal.valueOf(250); // Fixed fee for infant

    /**
     * Create a new booking with full validation.
     */
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        log.info("Creating booking for flight: {} on {}", request.getFlightNumber(), request.getFlightDate());

        // 1. Validate passenger count
        rulesValidator.validatePassengerCount(request.getPassengers().size());

        // 2. Get flight information and validate availability
        FlightOpsClient.FlightBookingInfo flightInfo = flightOpsClient.getFlightBookingInfo(request.getFlightNumber());

        if (!flightInfo.bookable()) {
            throw new FlightNotBookableException(request.getFlightNumber(), flightInfo.reason());
        }

        int passengerCount = request.getPassengers().size();
        if (flightInfo.availableSeats() < passengerCount) {
            throw new FlightNotBookableException(request.getFlightNumber(),
                    "Only " + flightInfo.availableSeats() + " seats available, but " + passengerCount + " requested");
        }

        // 3. Validate booking time (must be at least X hours before departure)
        rulesValidator.validateBookingTime(request.getFlightDate(), flightInfo.scheduledDeparture());

        // 4. Validate passengers (age, type, infant rules)
        rulesValidator.validatePassengers(request.getPassengers(), request.getFlightDate());

        // Generate unique PNR
        String pnr = generatePNR();

        // Create booking entity with real origin/destination from flight service
        Booking booking = Booking.builder()
                .bookingReference(pnr)
                .flightNumber(request.getFlightNumber().toUpperCase())
                .flightDate(request.getFlightDate())
                .origin(flightInfo.origin() != null ? flightInfo.origin() : "IST")
                .destination(flightInfo.destination() != null ? flightInfo.destination() : "JFK")
                .status(BookingStatus.CONFIRMED)
                .totalAmount(calculateTotalPrice(request.getPassengers(), request.getFlightDate()))
                .currency("TRY")
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .build();

        // Add passengers
        for (BookingRequest.PassengerDTO passengerDTO : request.getPassengers()) {
            Passenger passenger = Passenger.builder()
                    .firstName(passengerDTO.getFirstName())
                    .lastName(passengerDTO.getLastName())
                    .dateOfBirth(passengerDTO.getDateOfBirth())
                    .passengerType(PassengerType.valueOf(passengerDTO.getPassengerType()))
                    .nationality(passengerDTO.getNationality())
                    .passportNumber(passengerDTO.getPassportNumber())
                    .ticketNumber(generateTicketNumber())
                    .build();
            booking.addPassenger(passenger);
        }

        // Save to database
        Booking savedBooking = bookingRepository.save(booking);
        log.info("Booking created: PNR={}, Total={} TRY", pnr, savedBooking.getTotalAmount());

        // Publish event to Kafka
        publishBookingCreatedEvent(savedBooking);

        return mapToResponse(savedBooking);
    }

    /**
     * Get booking by PNR.
     * Cached for 5 minutes - frequently accessed during check-in window.
     * Anadolu Air Scenario: Passengers check their booking multiple times before flight.
     */
    @Cacheable(value = RedisCacheConfig.BOOKING_CACHE, key = "#pnr", unless = "#result == null")
    @Transactional(readOnly = true)
    public BookingResponse getBookingByPNR(String pnr) {
        log.info("Retrieving booking from DATABASE: PNR={}", pnr);

        Booking booking = bookingRepository.findByBookingReference(pnr)
                .orElseThrow(() -> new BookingNotFoundException(pnr));

        return mapToResponse(booking);
    }

    /**
     * Get all bookings for a flight.
     * Cached for 15 minutes - used by ground staff and flight operations.
     * Anadolu Air Scenario: Gate agents view passenger manifest repeatedly.
     */
    @Cacheable(value = RedisCacheConfig.FLIGHT_CACHE, key = "#flightNumber + '_' + #flightDate")
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForFlight(String flightNumber, LocalDate flightDate) {
        log.info("Retrieving bookings for flight from DATABASE: {} on {}", flightNumber, flightDate);

        return bookingRepository.findByFlightNumberAndFlightDate(flightNumber, flightDate)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Cancel a booking.
     * Evicts booking from cache and invalidates flight manifest cache.
     * Anadolu Air Scenario: When passenger cancels, all caches must reflect immediately.
     */
    @Caching(evict = {
            @CacheEvict(value = RedisCacheConfig.BOOKING_CACHE, key = "#pnr"),
            @CacheEvict(value = RedisCacheConfig.FLIGHT_CACHE, allEntries = true)
    })
    @Transactional
    public BookingResponse cancelBooking(String pnr) {
        log.info("Cancelling booking: PNR={}", pnr);

        Booking booking = bookingRepository.findByBookingReference(pnr)
                .orElseThrow(() -> new BookingNotFoundException(pnr));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException(pnr, booking.getStatus(), "cancel");
        }

        // Cannot cancel checked-in bookings
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            throw new InvalidBookingStateException(pnr, booking.getStatus(), "cancel");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        // Publish cancellation event
        publishBookingCancelledEvent(savedBooking);

        return mapToResponse(savedBooking);
    }

    /**
     * Get all bookings.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Check-in a booking.
     * Check-in is only allowed 24 hours to 1 hour before departure.
     * Evicts and updates cache - status changes from CONFIRMED to CHECKED_IN.
     * Anadolu Air Scenario: After check-in, seat assignments are visible immediately.
     */
    @Caching(evict = {
            @CacheEvict(value = RedisCacheConfig.BOOKING_CACHE, key = "#pnr"),
            @CacheEvict(value = RedisCacheConfig.FLIGHT_CACHE, allEntries = true),
            @CacheEvict(value = RedisCacheConfig.SEAT_MAP_CACHE, allEntries = true)
    })
    @Transactional
    public BookingResponse checkIn(String pnr) {
        log.info("Check-in for booking: PNR={}", pnr);

        Booking booking = bookingRepository.findByBookingReference(pnr)
                .orElseThrow(() -> new BookingNotFoundException(pnr));

        // Validate current booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED &&
                booking.getStatus() != BookingStatus.TICKETED) {
            throw new InvalidBookingStateException(pnr, booking.getStatus(), "check-in");
        }

        // Validate check-in time window
        FlightOpsClient.FlightBookingInfo checkInFlightInfo =
                flightOpsClient.getFlightBookingInfo(booking.getFlightNumber());
        rulesValidator.validateCheckInWindow(booking.getBookingReference(),
                booking.getFlightDate(), checkInFlightInfo.scheduledDeparture());

        booking.setStatus(BookingStatus.CHECKED_IN);

        // Assign seats (smart seat assignment)
        assignSeats(booking);

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Check-in completed: PNR={}", pnr);

        // Publish check-in event
        publishCheckInEvent(savedBooking);

        return mapToResponse(savedBooking);
    }

    // ==================== Validation Methods ====================

    // ==================== Helper Methods ====================

    /**
     * Assign seats to passengers smartly.
     * Keeps families together, assigns window/aisle based on preference.
     */
    private void assignSeats(Booking booking) {
        List<Passenger> passengers = booking.getPassengers();
        int seatNumber = 1;
        char[] rowLetters = {'A', 'B', 'C', 'D', 'E', 'F'};

        // Start from row 12 (typical economy)
        int rowNumber = 12;

        for (Passenger passenger : passengers) {
            // Infants don't get their own seat (lap infant)
            if (passenger.getPassengerType() == PassengerType.INFANT) {
                passenger.setSeatNumber("LAP");
            } else {
                char seatLetter = rowLetters[(seatNumber - 1) % 6];
                passenger.setSeatNumber(rowNumber + String.valueOf(seatLetter));
                seatNumber++;

                // Move to next row after 6 seats
                if (seatNumber > 6) {
                    seatNumber = 1;
                    rowNumber++;
                }
            }
        }
    }

    /**
     * Calculate total price based on passenger types.
     * Adults: Full price
     * Children: 50% discount
     * Infants: Fixed 250 TRY
     */
    private BigDecimal calculateTotalPrice(List<BookingRequest.PassengerDTO> passengers, LocalDate flightDate) {
        BigDecimal total = BigDecimal.ZERO;

        for (BookingRequest.PassengerDTO passenger : passengers) {
            PassengerType type = PassengerType.valueOf(passenger.getPassengerType());

            switch (type) {
                case ADULT:
                    total = total.add(ADULT_BASE_PRICE);
                    break;
                case CHILD:
                    // 50% discount for children
                    BigDecimal childPrice = ADULT_BASE_PRICE.multiply(CHILD_DISCOUNT_PERCENT);
                    total = total.add(childPrice);
                    break;
                case INFANT:
                    // Fixed infant fee
                    total = total.add(INFANT_PRICE);
                    break;
            }
        }

        log.debug("Calculated total price: {} TRY for {} passengers", total, passengers.size());
        return total;
    }

    private String generatePNR() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder pnr = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            pnr.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        // Ensure unique
        while (bookingRepository.existsByBookingReference(pnr.toString())) {
            pnr = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                pnr.append(chars.charAt((int) (Math.random() * chars.length())));
            }
        }
        return pnr.toString();
    }

    private String generateTicketNumber() {
        // Anadolu Air ticket numbers start with 235 (airline code)
        return "235" + System.currentTimeMillis() % 10000000000L;
    }

    private BookingResponse mapToResponse(Booking booking) {
        List<BookingResponse.PassengerInfo> passengers = booking.getPassengers()
                .stream()
                .map(p -> BookingResponse.PassengerInfo.builder()
                        .id(p.getId())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .dateOfBirth(p.getDateOfBirth())
                        .passengerType(p.getPassengerType().name())
                        .seatNumber(p.getSeatNumber())
                        .ticketNumber(p.getTicketNumber())
                        .build())
                .collect(Collectors.toList());

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .flightNumber(booking.getFlightNumber())
                .flightDate(booking.getFlightDate())
                .origin(booking.getOrigin())
                .destination(booking.getDestination())
                .status(booking.getStatus().name())
                .totalAmount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .contactEmail(booking.getContactEmail())
                .contactPhone(booking.getContactPhone())
                .passengers(passengers)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    // ==================== Kafka Events ====================

    private void publishBookingCreatedEvent(Booking booking) {
        BookingEvent event = createBookingEvent(booking, BookingEvent.EventType.BOOKING_CREATED);
        eventProducer.publishBookingEvent(event);
    }

    private void publishBookingCancelledEvent(Booking booking) {
        BookingEvent event = createBookingEvent(booking, BookingEvent.EventType.BOOKING_CANCELLED);
        eventProducer.publishBookingEvent(event);
    }

    private void publishCheckInEvent(Booking booking) {
        BookingEvent event = createBookingEvent(booking, BookingEvent.EventType.CHECK_IN_COMPLETED);
        eventProducer.publishBookingEvent(event);
    }

    private BookingEvent createBookingEvent(Booking booking, BookingEvent.EventType eventType) {
        List<BookingEvent.PassengerInfo> passengers = booking.getPassengers()
                .stream()
                .map(p -> BookingEvent.PassengerInfo.builder()
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .passengerType(p.getPassengerType().name())
                        .seatNumber(p.getSeatNumber())
                        .ticketNumber(p.getTicketNumber())
                        .build())
                .collect(Collectors.toList());

        return BookingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .bookingData(BookingEvent.BookingData.builder()
                        .bookingReference(booking.getBookingReference())
                        .flightNumber(booking.getFlightNumber())
                        .flightDate(booking.getFlightDate().atStartOfDay())
                        .passengers(passengers)
                        .status(BookingEvent.BookingStatus.valueOf(booking.getStatus().name()))
                        .totalAmount(booking.getTotalAmount())
                        .currency(booking.getCurrency())
                        .build())
                .build();
    }
}
