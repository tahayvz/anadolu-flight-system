package com.anadoluair.flight.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/** PNR koduyla bir rezervasyonu sorgular. */
@Component
public class BookingLookupTool implements Tool {

    private final FlightServices services;

    public BookingLookupTool(FlightServices services) {
        this.services = services;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "booking_lookup",
                "PNR kodu ile bir rezervasyonu bulur. Ucus numarasini, tarihini, "
                        + "yolcu sayisini ve rezervasyonun durumunu doner.",
                Map.of("pnr", "6 karakterli PNR kodu, ornek: ABC123")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String pnr = String.valueOf(arguments.get("pnr"));
        Map<String, Object> booking = services.bookingByPnr(pnr);

        if (booking == null) {
            return "%s kodlu rezervasyon bulunamadi.".formatted(pnr);
        }

        Object passengers = booking.get("passengers");
        int passengerCount = passengers instanceof java.util.List<?> list ? list.size() : 0;

        return "Rezervasyon %s: ucus %s, tarih %s, %d yolcu, durum %s.".formatted(
                booking.get("bookingReference"),
                booking.get("flightNumber"),
                booking.get("flightDate"),
                passengerCount,
                booking.get("status"));
    }
}
