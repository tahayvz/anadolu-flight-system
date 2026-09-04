package com.anadoluair.flight.integrationservice.notification;

import com.anadoluair.flight.events.BookingEvent;

/**
 * Gonderilecek bildirimin icerigi.
 * <p>
 * Olaydan uretilir; gonderim kanalindan (e-posta, SMS) bagimsizdir. Boylece ayni
 * icerik farkli kanallara verilebilir ve icerik uretimi tek basina test edilebilir.
 */
public record BookingNotification(String recipient, String subject, String body) {

    /** Bildirim gonderilebilir mi? Alici yoksa gonderilemez. */
    public boolean isDeliverable() {
        return recipient != null && !recipient.isBlank();
    }

    public static BookingNotification from(BookingEvent event) {
        BookingEvent.BookingData booking = event.getBookingData();
        String pnr = booking.getBookingReference();

        return new BookingNotification(
                booking.getContactEmail(),
                subjectFor(event.getEventType(), pnr),
                bodyFor(event));
    }

    private static String subjectFor(BookingEvent.EventType type, String pnr) {
        return switch (type) {
            case BOOKING_CREATED -> "Rezervasyonunuz alindi - " + pnr;
            case BOOKING_CONFIRMED -> "E-biletiniz hazir - " + pnr;
            case BOOKING_CANCELLED -> "Rezervasyonunuz iptal edildi - " + pnr;
            case BOOKING_MODIFIED -> "Rezervasyonunuz guncellendi - " + pnr;
            case CHECK_IN_COMPLETED -> "Check-in tamamlandi - " + pnr;
            case SEAT_SELECTED -> "Koltuk seciminiz kaydedildi - " + pnr;
        };
    }

    private static String bodyFor(BookingEvent event) {
        BookingEvent.BookingData booking = event.getBookingData();
        StringBuilder body = new StringBuilder();

        body.append("Rezervasyon kodu : ").append(booking.getBookingReference()).append('\n');
        body.append("Ucus             : ").append(booking.getFlightNumber()).append('\n');

        if (booking.getFlightDate() != null) {
            body.append("Tarih            : ").append(booking.getFlightDate().toLocalDate()).append('\n');
        }
        if (booking.getTotalAmount() != null) {
            body.append("Tutar            : ")
                .append(booking.getTotalAmount()).append(' ')
                .append(booking.getCurrency() == null ? "" : booking.getCurrency())
                .append('\n');
        }

        if (booking.getPassengers() != null && !booking.getPassengers().isEmpty()) {
            body.append("\nYolcular:\n");
            for (BookingEvent.PassengerInfo passenger : booking.getPassengers()) {
                body.append("  - ")
                    .append(passenger.getFirstName()).append(' ').append(passenger.getLastName());
                if (passenger.getSeatNumber() != null) {
                    body.append("  (koltuk ").append(passenger.getSeatNumber()).append(')');
                }
                body.append('\n');
            }
        }

        return body.toString();
    }
}
