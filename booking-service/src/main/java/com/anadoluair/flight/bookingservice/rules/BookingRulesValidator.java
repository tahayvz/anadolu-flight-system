package com.anadoluair.flight.bookingservice.rules;

import com.anadoluair.flight.bookingservice.config.BookingRulesConfig;
import com.anadoluair.flight.bookingservice.dto.BookingRequest;
import com.anadoluair.flight.bookingservice.entity.Passenger.PassengerType;
import com.anadoluair.flight.bookingservice.exception.BookingValidationException;
import com.anadoluair.flight.bookingservice.exception.CheckInNotAllowedException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Rezervasyon iş kuralları.
 *
 * <p><b>Neden ayrı bir sınıf?</b> Bu kurallar daha önce {@code BookingService} içinde
 * private metotlardı. Doğru çalıştıklarını görmek için tüm Spring bağlamını, veritabanını
 * ve Kafka'yı ayağa kaldırmak gerekiyordu — yani "yolcu yaşı tipine uyuyor mu?" sorusunu
 * yanıtlamak 40 saniye sürüyordu. Kurallar burada saf metotlar olduğu için çıplak JUnit
 * ile milisaniyede sınanır.
 *
 * <p><b>Neden {@link Clock} enjekte ediliyor?</b> Kuralların yarısı "şu ana" göre karar
 * verir (rezervasyon çok mu geç, check-in penceresi açık mı). {@code LocalDateTime.now()}
 * doğrudan çağrıldığında bu kurallar ancak gerçek zamanla test edilebilir; testler ya
 * bekler ya da günün saatine göre kararsızlaşır. Saat dışarıdan verilince zaman
 * sabitlenebilir.
 */
public class BookingRulesValidator {

    private static final LocalTime UNKNOWN_DEPARTURE_DEFAULT = LocalTime.of(12, 0);
    private static final DateTimeFormatter CHECK_IN_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private final BookingRulesConfig rules;
    private final Clock clock;

    public BookingRulesValidator(BookingRulesConfig rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    /** Tek rezervasyondaki yolcu sayısı üst sınırı. */
    public void validatePassengerCount(int count) {
        if (count > rules.getMaxPassengers()) {
            throw BookingValidationException.tooManyPassengers(rules.getMaxPassengers());
        }
    }

    /** Rezervasyon, kalkıştan en az {@code minBookingHours} saat önce yapılmalıdır. */
    public void validateBookingTime(LocalDate flightDate, LocalTime scheduledDeparture) {
        LocalDateTime departure = departureOf(flightDate, scheduledDeparture);
        LocalDateTime latestAllowed = departure.minusHours(rules.getMinBookingHours());

        if (now().isAfter(latestAllowed)) {
            throw BookingValidationException.tooCloseToDeperture(rules.getMinBookingHours());
        }
    }

    /**
     * Yolcu listesini doğrular: her yolcunun yaşı bildirdiği tipe uymalı ve bebek
     * kuralları sağlanmalıdır.
     */
    public void validatePassengers(List<BookingRequest.PassengerDTO> passengers, LocalDate flightDate) {
        int adults = 0;
        int infants = 0;

        for (BookingRequest.PassengerDTO passenger : passengers) {
            PassengerType type = PassengerType.valueOf(passenger.getPassengerType());
            validateAgeMatchesType(passenger, ageOn(passenger.getDateOfBirth(), flightDate), type);

            if (type == PassengerType.ADULT) {
                adults++;
            } else if (type == PassengerType.INFANT) {
                infants++;
            }
        }

        validateInfantRatio(adults, infants);
    }

    /**
     * Check-in penceresi: kalkıştan {@code checkinOpenHours} saat önce açılır,
     * {@code checkinCloseHours} saat önce kapanır.
     */
    public void validateCheckInWindow(String bookingReference, LocalDate flightDate,
                                      LocalTime scheduledDeparture) {
        LocalDateTime departure = departureOf(flightDate, scheduledDeparture);
        LocalDateTime opens = departure.minusHours(rules.getCheckinOpenHours());
        LocalDateTime closes = departure.minusHours(rules.getCheckinCloseHours());
        LocalDateTime now = now();

        if (now.isBefore(opens)) {
            long hoursUntilOpen = Duration.between(now, opens).toHours();
            throw new CheckInNotAllowedException(bookingReference, String.format(
                    "Check-in opens in %d hours (at %s)",
                    hoursUntilOpen, opens.format(CHECK_IN_TIME_FORMAT)));
        }

        if (now.isAfter(closes)) {
            throw new CheckInNotAllowedException(bookingReference,
                    "Check-in has closed. Please proceed to the airport counter.");
        }
    }

    /** Uçuş tarihindeki yaş — doğum gününe göre, bugüne göre değil. */
    public static int ageOn(LocalDate dateOfBirth, LocalDate flightDate) {
        return Period.between(dateOfBirth, flightDate).getYears();
    }

    private void validateAgeMatchesType(BookingRequest.PassengerDTO passenger, int age,
                                        PassengerType type) {
        String fullName = passenger.getFirstName() + " " + passenger.getLastName();

        boolean valid = switch (type) {
            case ADULT -> age > rules.getChildMaxAge();
            case CHILD -> age >= rules.getChildMinAge() && age <= rules.getChildMaxAge();
            case INFANT -> age < rules.getInfantMaxAge();
        };

        if (!valid) {
            throw BookingValidationException.invalidPassengerAge(fullName, type.name(), age);
        }
    }

    private void validateInfantRatio(int adults, int infants) {
        if (infants == 0 || !rules.isInfantRequiresAdult()) {
            return;
        }
        if (adults == 0) {
            throw BookingValidationException.infantRequiresAdult();
        }
        if (infants > adults * rules.getMaxInfantsPerAdult()) {
            throw BookingValidationException.tooManyInfantsPerAdult(rules.getMaxInfantsPerAdult());
        }
    }

    /** Kalkış saati bilinmiyorsa öğlen varsayılır — kuralların tek bir referansı olsun. */
    private LocalDateTime departureOf(LocalDate flightDate, LocalTime scheduledDeparture) {
        return LocalDateTime.of(flightDate,
                scheduledDeparture == null ? UNKNOWN_DEPARTURE_DEFAULT : scheduledDeparture);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
