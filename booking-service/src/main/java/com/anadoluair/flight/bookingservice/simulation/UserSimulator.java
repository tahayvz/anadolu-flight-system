package com.anadoluair.flight.bookingservice.simulation;

import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.dto.BookingRequest.PassengerDTO;
import com.anadoluair.flight.bookingservice.dto.BookingResponse;
import com.anadoluair.flight.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates real user traffic for testing and demo purposes.
 *
 * Enable/Disable via REST API:
 * POST /api/simulation/start
 * POST /api/simulation/stop
 * GET  /api/simulation/stats
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSimulator {

    private final BookingService bookingService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulBookings = new AtomicLong(0);
    private final AtomicLong failedBookings = new AtomicLong(0);
    private final AtomicLong totalCheckins = new AtomicLong(0);
    private final AtomicLong totalCancellations = new AtomicLong(0);
    private final AtomicLong totalGetByPnr = new AtomicLong(0);
    private final AtomicLong totalGetAll = new AtomicLong(0);
    private final AtomicLong totalGetByFlight = new AtomicLong(0);

    private final Random random = new Random();

    // Tüm uçuşları kullan (flight-ops'ta mevcut)
    private static final String[] FLIGHTS = {"ZZ1", "ZZ7", "ZZ11", "ZZ77", "ZZ123", "ZZ1984", "ZZ2001", "ZZ2010", "ZZ2020"};
    private static final String[] FIRST_NAMES = {"Ahmet", "Mehmet", "Ali", "Ayse", "Fatma", "Zeynep", "Mustafa", "Emine", "Hasan", "Huseyin"};
    private static final String[] LAST_NAMES = {"Yilmaz", "Kaya", "Demir", "Celik", "Sahin", "Yildiz", "Ozturk", "Aydin", "Ozdemir", "Arslan"};
    private static final String[] DOMAINS = {"gmail.com", "hotmail.com", "yahoo.com", "outlook.com"};

    private final List<String> createdPNRs = new ArrayList<>();

    /**
     * Start simulation
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("🚀 User simulation STARTED");
            resetStats();
        }
    }

    /**
     * Stop simulation
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("🛑 User simulation STOPPED");
        }
    }

    /**
     * Check if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Simulated user actions - runs every 2 seconds when enabled
     */
    @Scheduled(fixedDelay = 2000)
    public void simulateUserAction() {
        if (!running.get()) {
            return;
        }

        totalRequests.incrementAndGet();

        // Random action distribution
        int action = random.nextInt(100);

        try {
            if (action < 40) {
                // 40% booking
                simulateBooking();
            } else if (action < 55) {
                // 15% get by PNR
                simulateGetBookingByPnr();
            } else if (action < 65) {
                // 10% get all
                simulateGetAllBookings();
            } else if (action < 75) {
                // 10% get by flight
                simulateGetBookingsForFlight();
            } else if (action < 90) {
                // 15% checkin
                simulateCheckin();
            } else {
                // 10% cancel
                simulateCancellation();
            }
        } catch (Exception e) {
            log.debug("Simulation action failed: {}", e.getMessage());
        }
    }

    /**
     * Simulate burst bookings - runs every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void simulateBurstBookings() {
        if (!running.get()) {
            return;
        }

        int burstSize = random.nextInt(2) + 1;
        log.debug("📊 Simulating burst of {} bookings", burstSize);

        for (int i = 0; i < burstSize; i++) {
            try {
                simulateBooking();
            } catch (Exception e) {
                log.debug("Burst booking failed: {}", e.getMessage());
            }
        }
    }

    private void simulateBooking() {
        BookingRequest request = generateRandomBookingRequest();

        try {
            BookingResponse response = bookingService.createBooking(request);

            if (response == null || response.getBookingReference() == null) {
                failedBookings.incrementAndGet();
                log.debug("❌ [SIMULATION] Booking failed: empty response");
                return;
            }

            successfulBookings.incrementAndGet();

            synchronized (createdPNRs) {
                createdPNRs.add(response.getBookingReference());
                if (createdPNRs.size() > 50) {
                    createdPNRs.remove(0);
                }
            }

            log.info("✅ [SIMULATION] Booking created: PNR={}, Flight={}, Passengers={}",
                    response.getBookingReference(), request.getFlightNumber(), request.getPassengers().size());
        } catch (Exception e) {
            failedBookings.incrementAndGet();
            log.debug("❌ [SIMULATION] Booking failed: {}", e.getMessage());
        }
    }

    private void simulateCheckin() {
        String pnr = getRandomPNR();
        if (pnr == null) {
            return;
        }

        try {
            bookingService.checkIn(pnr);
            totalCheckins.incrementAndGet();
            log.info("✈️ [SIMULATION] Check-in completed: PNR={}", pnr);
        } catch (Exception e) {
            log.debug("❌ [SIMULATION] Check-in failed for {}: {}", pnr, e.getMessage());
        }
    }

    private void simulateCancellation() {
        String pnr = getRandomPNR();
        if (pnr == null) {
            return;
        }

        try {
            bookingService.cancelBooking(pnr);
            totalCancellations.incrementAndGet();

            synchronized (createdPNRs) {
                createdPNRs.remove(pnr);
            }

            log.info("🚫 [SIMULATION] Booking cancelled: PNR={}", pnr);
        } catch (Exception e) {
            log.debug("❌ [SIMULATION] Cancellation failed for {}: {}", pnr, e.getMessage());
        }
    }

    private void simulateGetBookingByPnr() {
        String pnr = getRandomPNR();
        if (pnr == null) {
            return;
        }

        try {
            bookingService.getBookingByPNR(pnr);
            totalGetByPnr.incrementAndGet();
            log.info("🔎 [SIMULATION] Booking fetched by PNR: {}", pnr);
        } catch (Exception e) {
            log.debug("❌ [SIMULATION] Get by PNR failed for {}: {}", pnr, e.getMessage());
        }
    }

    private void simulateGetAllBookings() {
        try {
            List<BookingResponse> bookings = bookingService.getAllBookings();
            totalGetAll.incrementAndGet();
            log.info("📋 [SIMULATION] All bookings fetched: count={}", bookings.size());
        } catch (Exception e) {
            log.debug("❌ [SIMULATION] Get all bookings failed: {}", e.getMessage());
        }
    }

    private void simulateGetBookingsForFlight() {
        String flightNumber = FLIGHTS[random.nextInt(FLIGHTS.length)];
        LocalDate flightDate = LocalDate.now().plusDays(random.nextInt(30) + 1);

        try {
            List<BookingResponse> bookings = bookingService.getBookingsForFlight(flightNumber, flightDate);
            totalGetByFlight.incrementAndGet();
            log.info("🛫 [SIMULATION] Flight bookings fetched: flight={}, date={}, count={}",
                    flightNumber, flightDate, bookings.size());
        } catch (Exception e) {
            log.debug("❌ [SIMULATION] Get bookings by flight failed: {}", e.getMessage());
        }
    }

    private BookingRequest generateRandomBookingRequest() {
        BookingRequest request = new BookingRequest();

        // Rastgele bir uçuş seç
        request.setFlightNumber(FLIGHTS[random.nextInt(FLIGHTS.length)]);

        // Random date: 1-30 days from now
        request.setFlightDate(LocalDate.now().plusDays(random.nextInt(30) + 1));

        // Random passengers: 1-3
        int passengerCount = random.nextInt(3) + 1;
        List<PassengerDTO> passengers = new ArrayList<>();

        for (int i = 0; i < passengerCount; i++) {
            passengers.add(generateRandomPassenger());
        }
        request.setPassengers(passengers);

        // Contact info
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)].toLowerCase();
        String domain = DOMAINS[random.nextInt(DOMAINS.length)];
        request.setContactEmail(firstName + random.nextInt(1000) + "@" + domain);
        request.setContactPhone("+9053" + (random.nextInt(90000000) + 10000000));

        return request;
    }

    private PassengerDTO generateRandomPassenger() {
        PassengerDTO passenger = new PassengerDTO();

        passenger.setFirstName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]);
        passenger.setLastName(LAST_NAMES[random.nextInt(LAST_NAMES.length)]);

        // Random age: 18-70 years old (adult)
        int age = random.nextInt(52) + 18;
        passenger.setDateOfBirth(LocalDate.now().minusYears(age).minusDays(random.nextInt(365)));

        // Random passport
        passenger.setPassportNumber("U" + (10000000 + random.nextInt(90000000)));
        passenger.setNationality("TR");
        passenger.setPassengerType("ADULT");

        return passenger;
    }

    private String getRandomPNR() {
        synchronized (createdPNRs) {
            if (createdPNRs.isEmpty()) {
                return null;
            }
            return createdPNRs.get(random.nextInt(createdPNRs.size()));
        }
    }

    private void resetStats() {
        totalRequests.set(0);
        successfulBookings.set(0);
        failedBookings.set(0);
        totalCheckins.set(0);
        totalCancellations.set(0);
        totalGetByPnr.set(0);
        totalGetAll.set(0);
        totalGetByFlight.set(0);
        synchronized (createdPNRs) {
            createdPNRs.clear();
        }
    }

    /**
     * Get simulation statistics
     */
    public SimulationStats getStats() {
        return new SimulationStats(
                running.get(),
                totalRequests.get(),
                successfulBookings.get(),
                failedBookings.get(),
                totalCheckins.get(),
                totalCancellations.get(),
                totalGetByPnr.get(),
                totalGetAll.get(),
                totalGetByFlight.get(),
                createdPNRs.size()
        );
    }

    public record SimulationStats(
            boolean running,
            long totalRequests,
            long successfulBookings,
            long failedBookings,
            long totalCheckins,
            long totalCancellations,
            long totalGetByPnr,
            long totalGetAll,
            long totalGetByFlight,
            int activePNRs
    ) {}
}
