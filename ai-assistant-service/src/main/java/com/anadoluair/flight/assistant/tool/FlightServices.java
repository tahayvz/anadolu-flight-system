package com.anadoluair.flight.assistant.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Uçuş servislerine yapılan HTTP çağrılarını tek yerde toplar.
 *
 * <p>Zaman aşımı bilinçli olarak kısa. Bir araç yavaş cevap verirse agent döngüsü
 * bekler, kullanıcı da bekler. Servis ayakta değilse hızlıca öğrenmek, uzun süre
 * asılı kalmaktan iyidir.
 */
@Component
public class FlightServices {

    private final RestClient flightOps;
    private final RestClient booking;

    public FlightServices(RestClient.Builder builder,
                          @Value("${assistant.flight-ops.base-url}") String flightOpsUrl,
                          @Value("${assistant.booking.base-url}") String bookingUrl) {
        this.flightOps = builder.clone().baseUrl(flightOpsUrl).build();
        this.booking = builder.clone().baseUrl(bookingUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> flightStatus(String flightNumber) {
        return flightOps.get()
                .uri("/api/flights/{flightNumber}", flightNumber)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> bookable(String flightNumber) {
        return flightOps.get()
                .uri("/api/flights/{flightNumber}/bookable", flightNumber)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> bookingByPnr(String pnr) {
        return booking.get()
                .uri("/api/bookings/{pnr}", pnr)
                .retrieve()
                .body(Map.class);
    }

    /** Testlerin ve yapılandırmanın ortak kullandığı istemci zaman aşımı. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);
}
