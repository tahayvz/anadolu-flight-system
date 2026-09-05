package com.anadoluair.flight.assistant.tool;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Uçuş servislerine yapılan HTTP çağrılarını tek yerde toplar.
 *
 * <p>Zaman aşımı kısa tutuldu. Bir araç yavaş cevap verirse agent döngüsü bekler,
 * kullanıcı da bekler. Servis ayakta değilse hızlıca öğrenmek, uzun süre asılı
 * kalmaktan iyidir.
 */
@Component
public class FlightServices {

    private final RestClient flightOps;
    private final RestClient booking;

    public FlightServices(RestClient.Builder builder,
                          @Value("${assistant.flight-ops.base-url}") String flightOpsUrl,
                          @Value("${assistant.booking.base-url}") String bookingUrl) {
        ClientHttpRequestFactory factory = timeoutFactory();
        this.flightOps = builder.clone().baseUrl(flightOpsUrl).requestFactory(factory).build();
        this.booking = builder.clone().baseUrl(bookingUrl).requestFactory(factory).build();
    }

    /**
     * Zaman aşımı olan istek fabrikası.
     *
     * <p>Bu sınıf bir süre boyunca {@code TIMEOUT} sabitini tanımlıyor ama hiçbir
     * yere VERMİYORDU. Javadoc kısa zaman aşımı olduğunu söylüyordu, gerçekte hiç
     * zaman aşımı yoktu: {@code RestClient} varsayılanı süresiz bekler.
     *
     * <p>Etkisi tek bir yavaş çağrıdan büyük: agent bir soru için en fazla
     * {@code assistant.max-turns} kez araç çağırır, yani askıda kalan bir servis
     * tek istekte birden fazla iş parçacığını tutabilir.
     */
    private static ClientHttpRequestFactory timeoutFactory() {
        return ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(TIMEOUT));
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

    /** Yanıt bekleme süresi. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Bağlantı kurma süresi. Servis kapalıysa hızlıca öğrenmek istiyoruz. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
}
