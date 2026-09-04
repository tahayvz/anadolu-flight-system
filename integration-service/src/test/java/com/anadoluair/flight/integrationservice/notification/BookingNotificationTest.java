package com.anadoluair.flight.integrationservice.notification;

import com.anadoluair.flight.events.BookingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bildirim iceriginin uretimi. Spring, Kafka ya da posta sunucusu gerektirmez.
 */
@DisplayName("Bildirim icerigi")
class BookingNotificationTest {

    private BookingEvent event(BookingEvent.EventType type, String email) {
        return BookingEvent.builder()
                .eventId("evt-1")
                .eventType(type)
                .timestamp(LocalDateTime.of(2026, 1, 15, 10, 0))
                .bookingData(BookingEvent.BookingData.builder()
                        .bookingReference("ABC123")
                        .flightNumber("ZZ1234")
                        .flightDate(LocalDateTime.of(2026, 2, 1, 14, 30))
                        .status(BookingEvent.BookingStatus.CONFIRMED)
                        .totalAmount(new BigDecimal("2450.00"))
                        .currency("TRY")
                        .contactEmail(email)
                        .passengers(List.of(
                                BookingEvent.PassengerInfo.builder()
                                        .firstName("Ada").lastName("Yolcu")
                                        .passengerType("ADULT").seatNumber("12A").build(),
                                BookingEvent.PassengerInfo.builder()
                                        .firstName("Efe").lastName("Yolcu")
                                        .passengerType("CHILD").seatNumber("12B").build()))
                        .build())
                .build();
    }

    @ParameterizedTest
    @EnumSource(BookingEvent.EventType.class)
    @DisplayName("her olay turu icin konu uretilir ve PNR icerir")
    void shouldProduceSubjectForEveryEventType(BookingEvent.EventType type) {
        BookingNotification notification = BookingNotification.from(event(type, "a@b.example"));

        assertThat(notification.subject()).isNotBlank().contains("ABC123");
    }

    @Test
    @DisplayName("govde rezervasyon bilgilerini icerir")
    void bodyShouldContainBookingDetails() {
        BookingNotification notification =
                BookingNotification.from(event(BookingEvent.EventType.BOOKING_CONFIRMED, "a@b.example"));

        assertThat(notification.body())
                .contains("ABC123")
                .contains("ZZ1234")
                .contains("2026-02-01")
                .contains("2450.00")
                .contains("TRY");
    }

    @Test
    @DisplayName("govde tum yolculari ve koltuklari listeler")
    void bodyShouldListEveryPassenger() {
        BookingNotification notification =
                BookingNotification.from(event(BookingEvent.EventType.BOOKING_CONFIRMED, "a@b.example"));

        assertThat(notification.body())
                .contains("Ada Yolcu").contains("12A")
                .contains("Efe Yolcu").contains("12B");
    }

    @Test
    @DisplayName("alici adresi olay icinden gelir")
    void recipientShouldComeFromEvent() {
        assertThat(BookingNotification.from(event(BookingEvent.EventType.BOOKING_CREATED, "yolcu@ornek.example"))
                .recipient()).isEqualTo("yolcu@ornek.example");
    }

    @Test
    @DisplayName("e-posta adresi yoksa bildirim gonderilebilir sayilmaz")
    void shouldNotBeDeliverableWithoutRecipient() {
        assertThat(BookingNotification.from(event(BookingEvent.EventType.BOOKING_CREATED, null))
                .isDeliverable()).isFalse();
        assertThat(BookingNotification.from(event(BookingEvent.EventType.BOOKING_CREATED, "  "))
                .isDeliverable()).isFalse();
    }

    @Test
    @DisplayName("gecerli adres varsa gonderilebilir")
    void shouldBeDeliverableWithRecipient() {
        assertThat(BookingNotification.from(event(BookingEvent.EventType.BOOKING_CREATED, "a@b.example"))
                .isDeliverable()).isTrue();
    }
}
