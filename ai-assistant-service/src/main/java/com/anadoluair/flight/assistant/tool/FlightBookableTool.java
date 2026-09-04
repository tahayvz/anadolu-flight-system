package com.anadoluair.flight.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/** Bir uçuşun rezervasyona açık olup olmadığını ve boş koltuk sayısını döner. */
@Component
public class FlightBookableTool implements Tool {

    private final FlightServices services;

    public FlightBookableTool(FlightServices services) {
        this.services = services;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "flight_bookable",
                "Bir ucusun rezervasyona acik olup olmadigini soyler ve bos koltuk sayisini verir. "
                        + "Ucus kalkmis, dolmus veya iptal olmus olabilir.",
                Map.of("flightNumber", "Ucus numarasi, ornek: ZZ123")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String flightNumber = String.valueOf(arguments.get("flightNumber"));
        Map<String, Object> result = services.bookable(flightNumber);

        if (result == null) {
            return "%s numarali ucus bulunamadi.".formatted(flightNumber);
        }

        boolean bookable = Boolean.TRUE.equals(result.get("bookable"));
        return "Ucus %s %s. Gerekce: %s. Bos koltuk: %s.".formatted(
                flightNumber,
                bookable ? "rezervasyona ACIK" : "rezervasyona KAPALI",
                result.get("reason"),
                result.get("availableSeats"));
    }
}
