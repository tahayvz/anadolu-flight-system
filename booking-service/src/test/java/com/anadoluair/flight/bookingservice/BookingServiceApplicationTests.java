package com.anadoluair.flight.bookingservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uygulama bağlamının gerçekten ayağa kalktığını doğrular.
 * <p>
 * {@link AbstractBookingTest}'ten türer: aksi hâlde test {@code localhost:6379}'a
 * bağlanmayı dener, bulamaz ve bağlantı zaman aşımını bekler — geçse bile her koşuya
 * 40 saniye ekler.
 */
@DisplayName("Booking Service bağlamı")
class BookingServiceApplicationTests extends AbstractBookingTest {

    @Test
    @DisplayName("uygulama bağlamı yüklenir")
    void contextLoads() {
    }
}
