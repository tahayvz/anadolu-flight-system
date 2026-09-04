package com.anadoluair.flight.integrationservice.kafka;

import com.anadoluair.flight.events.BookingEvent;
import com.anadoluair.flight.events.KafkaTopics;
import com.anadoluair.flight.integrationservice.notification.BookingNotification;
import com.anadoluair.flight.integrationservice.notification.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Rezervasyon olaylarini dinler ve yolcuya bildirim gonderir.
 *
 * <h2>Kapsam</h2>
 * Bu servis, rezervasyon olaylarini <b>dis dunyaya</b> tasiyan katmandir. Su an tek bir
 * dis kanal gerceklenmistir: <b>e-posta</b>. Lokal calistirmada MailHog'a gonderilir ve
 * tarayicidan gorulebilir (http://localhost:8025).
 *
 * <p>Gercek bir havayolunda bu katman ayrica su sistemlere baglanirdi: kod paylasimli
 * (codeshare) partner havayollari, odeme gecidi (iade icin), DCS (Departure Control
 * System) ve harici koltuk haritasi. Bunlar bu projenin kapsami disindadir; sahte
 * istemciler yazmak yerine kapsam acikca belirtilmistir. Yeni bir kanal eklemek,
 * {@link NotificationSender} gibi bir arayuz ve onu cagiran bir satir demektir.
 *
 * <h2>Hata davranisi</h2>
 * Bildirim gonderilemezse olay tuketimi <b>basarisiz sayilmaz</b>. Rezervasyon
 * gecerlidir; yalnizca haber verilememistir. Istisna firlatilsaydi Kafka mesaji tekrar
 * islenir ve ayni rezervasyon icin defalarca posta denemesi yapilirdi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final NotificationSender notificationSender;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "bookingEventListenerContainerFactory"
    )
    public void consumeBookingEvent(BookingEvent event) {
        BookingEvent.BookingData booking = event.getBookingData();

        log.info("Rezervasyon olayi alindi: type={}, pnr={}, flight={}",
                event.getEventType(), booking.getBookingReference(), booking.getFlightNumber());

        notificationSender.send(BookingNotification.from(event));
    }
}
