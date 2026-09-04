package com.anadoluair.flight.bookingservice.rules;

import com.anadoluair.flight.bookingservice.config.BookingRulesConfig;
import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.exception.BookingValidationException;
import com.anadoluair.flight.bookingservice.exception.CheckInNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rezervasyon kurallarının testleri.
 * <p>
 * Spring bağlamı, veritabanı ya da Kafka yok. Saat sabit olduğu için zamana bağlı
 * kurallar da deterministik: testler günün saatine göre davranış değiştirmez.
 */
@DisplayName("Rezervasyon kuralları")
class BookingRulesValidatorTest {

    /** Sabit "şu an": 15 Ocak 2026, saat 10:00 UTC. */
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final LocalDate FLIGHT_DATE = LocalDate.of(2026, 1, 20);
    private static final LocalTime DEPARTURE = LocalTime.of(14, 0);

    private BookingRulesConfig rules;
    private BookingRulesValidator validator;

    @BeforeEach
    void setUp() {
        rules = new BookingRulesConfig();   // varsayılanlar: 9 yolcu, 24/1 saat check-in, vb.
        validator = new BookingRulesValidator(rules, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BookingRequest.PassengerDTO passenger(String type, LocalDate birth) {
        BookingRequest.PassengerDTO dto = new BookingRequest.PassengerDTO();
        dto.setFirstName("Ada");
        dto.setLastName("Yolcu");
        dto.setPassengerType(type);
        dto.setDateOfBirth(birth);
        return dto;
    }

    /** Uçuş tarihinde tam olarak {@code age} yaşında olacak bir yolcu. */
    private BookingRequest.PassengerDTO aged(String type, int age) {
        return passenger(type, FLIGHT_DATE.minusYears(age));
    }

    // ==================== yolcu sayısı ====================

    @Nested
    @DisplayName("Yolcu sayısı")
    class PassengerCount {

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 9})
        @DisplayName("üst sınıra kadar kabul edilir")
        void shouldAcceptUpToLimit(int count) {
            assertThatCode(() -> validator.validatePassengerCount(count)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 25})
        @DisplayName("üst sınırın üstü reddedilir")
        void shouldRejectAboveLimit(int count) {
            assertThatThrownBy(() -> validator.validatePassengerCount(count))
                    .isInstanceOf(BookingValidationException.class);
        }

        @Test
        @DisplayName("sınır yapılandırmadan okunur")
        void shouldUseConfiguredLimit() {
            rules.setMaxPassengers(2);

            assertThatCode(() -> validator.validatePassengerCount(2)).doesNotThrowAnyException();
            assertThatThrownBy(() -> validator.validatePassengerCount(3))
                    .isInstanceOf(BookingValidationException.class);
        }
    }

    // ==================== rezervasyon zamanı ====================

    @Nested
    @DisplayName("Rezervasyon zamanı")
    class BookingTime {

        @Test
        @DisplayName("kalkıştan günler önce yapılabilir")
        void shouldAllowWellBeforeDeparture() {
            assertThatCode(() -> validator.validateBookingTime(FLIGHT_DATE, DEPARTURE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("kalkışa 2 saatten az kaldıysa reddedilir")
        void shouldRejectTooCloseToDeparture() {
            // Şu an 10:00; bugün 11:00 kalkışa 1 saat var, minimum 2 saat.
            LocalDate today = LocalDate.ofInstant(NOW, ZoneId.of("UTC"));

            assertThatThrownBy(() ->
                    validator.validateBookingTime(today, LocalTime.of(11, 0)))
                    .isInstanceOf(BookingValidationException.class);
        }

        @Test
        @DisplayName("tam sınırda kabul edilir")
        void shouldAcceptExactlyAtLimit() {
            // Şu an 10:00, kalkış 12:00 → tam 2 saat
            LocalDate today = LocalDate.ofInstant(NOW, ZoneId.of("UTC"));

            assertThatCode(() -> validator.validateBookingTime(today, LocalTime.of(12, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("kalkış saati bilinmiyorsa öğlen varsayılır")
        void shouldFallBackToNoonWhenDepartureUnknown() {
            LocalDate today = LocalDate.ofInstant(NOW, ZoneId.of("UTC"));

            // null kalkış → 12:00 varsayılır → 10:00'da hâlâ 2 saat var, kabul
            assertThatCode(() -> validator.validateBookingTime(today, null))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== yolcu yaşı ve tipi ====================

    @Nested
    @DisplayName("Yolcu yaşı ve tipi")
    class PassengerAges {

        @ParameterizedTest
        @CsvSource({"ADULT,12", "ADULT,35", "CHILD,2", "CHILD,11", "INFANT,0", "INFANT,1"})
        @DisplayName("yaş bildirilen tiple uyuşuyorsa kabul edilir")
        void shouldAcceptMatchingAgeAndType(String type, int age) {
            List<BookingRequest.PassengerDTO> passengers = "INFANT".equals(type)
                    ? List.of(aged("ADULT", 30), aged(type, age))   // bebek yanında yetişkin şart
                    : List.of(aged(type, age));

            assertThatCode(() -> validator.validatePassengers(passengers, FLIGHT_DATE))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @CsvSource({"ADULT,11", "CHILD,1", "CHILD,12", "INFANT,2", "INFANT,5"})
        @DisplayName("yaş tiple uyuşmuyorsa reddedilir")
        void shouldRejectMismatchedAgeAndType(String type, int age) {
            List<BookingRequest.PassengerDTO> passengers = List.of(aged("ADULT", 30), aged(type, age));

            assertThatThrownBy(() -> validator.validatePassengers(passengers, FLIGHT_DATE))
                    .isInstanceOf(BookingValidationException.class);
        }

        @Test
        @DisplayName("yaş uçuş tarihine göre hesaplanır, bugüne göre değil")
        void shouldCalculateAgeOnFlightDate() {
            // 20 Ocak 2014 doğumlu: 15 Ocak 2026'da 11, 20 Ocak 2026'da 12 yaşında.
            LocalDate birth = LocalDate.of(2014, 1, 20);

            assertThat(BookingRulesValidator.ageOn(birth, LocalDate.of(2026, 1, 15))).isEqualTo(11);
            assertThat(BookingRulesValidator.ageOn(birth, FLIGHT_DATE)).isEqualTo(12);

            // Uçuş tarihinde 12 olduğu için ADULT geçerli olmalı
            assertThatCode(() -> validator.validatePassengers(
                    List.of(passenger("ADULT", birth)), FLIGHT_DATE))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== bebek kuralları ====================

    @Nested
    @DisplayName("Bebek kuralları")
    class InfantRules {

        @Test
        @DisplayName("yanında yetişkin olmayan bebek reddedilir")
        void shouldRejectInfantWithoutAdult() {
            assertThatThrownBy(() -> validator.validatePassengers(
                    List.of(aged("CHILD", 8), aged("INFANT", 1)), FLIGHT_DATE))
                    .isInstanceOf(BookingValidationException.class);
        }

        @Test
        @DisplayName("yetişkin başına bir bebek kabul edilir")
        void shouldAcceptOneInfantPerAdult() {
            assertThatCode(() -> validator.validatePassengers(
                    List.of(aged("ADULT", 30), aged("ADULT", 32),
                            aged("INFANT", 1), aged("INFANT", 0)), FLIGHT_DATE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("yetişkin başına birden fazla bebek reddedilir")
        void shouldRejectMoreInfantsThanAdults() {
            assertThatThrownBy(() -> validator.validatePassengers(
                    List.of(aged("ADULT", 30), aged("INFANT", 1), aged("INFANT", 0)), FLIGHT_DATE))
                    .isInstanceOf(BookingValidationException.class);
        }

        @Test
        @DisplayName("kural kapatılırsa yalnız bebek kabul edilir")
        void shouldAllowLoneInfantWhenRuleDisabled() {
            rules.setInfantRequiresAdult(false);

            assertThatCode(() -> validator.validatePassengers(
                    List.of(aged("INFANT", 1)), FLIGHT_DATE))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== check-in penceresi ====================

    @Nested
    @DisplayName("Check-in penceresi")
    class CheckInWindow {

        /** Şu an 10:00. Kalkış bugün 20:00 → pencere dün 20:00'de açıldı, 19:00'da kapanır. */
        private final LocalDate today = LocalDate.ofInstant(NOW, ZoneId.of("UTC"));

        @Test
        @DisplayName("pencere içinde kabul edilir")
        void shouldAcceptInsideWindow() {
            assertThatCode(() -> validator.validateCheckInWindow(
                    "PNR123", today, LocalTime.of(20, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pencere açılmadan önce reddedilir")
        void shouldRejectBeforeWindowOpens() {
            // Kalkış 3 gün sonra → check-in henüz açılmadı
            assertThatThrownBy(() -> validator.validateCheckInWindow(
                    "PNR123", today.plusDays(3), LocalTime.of(20, 0)))
                    .isInstanceOf(CheckInNotAllowedException.class)
                    .hasMessageContaining("opens in");
        }

        @Test
        @DisplayName("pencere kapandıktan sonra reddedilir")
        void shouldRejectAfterWindowCloses() {
            // Kalkış bugün 10:30 → kapanış 09:30'du, şu an 10:00
            assertThatThrownBy(() -> validator.validateCheckInWindow(
                    "PNR123", today, LocalTime.of(10, 30)))
                    .isInstanceOf(CheckInNotAllowedException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("pencere yapılandırmadan okunur")
        void shouldUseConfiguredWindow() {
            rules.setCheckinOpenHours(2);   // yalnızca kalkıştan 2 saat önce açılır

            // Kalkış bugün 20:00 → 18:00'de açılır, şu an 10:00 → henüz erken
            assertThatThrownBy(() -> validator.validateCheckInWindow(
                    "PNR123", today, LocalTime.of(20, 0)))
                    .isInstanceOf(CheckInNotAllowedException.class);
        }
    }
}
