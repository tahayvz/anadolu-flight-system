package com.anadoluair.flight.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/** Bir uçuşun güncel durumunu (kalkış, varış, saat, durum) döner. */
@Component
public class FlightStatusTool implements Tool {

    private final FlightServices services;

    public FlightStatusTool(FlightServices services) {
        this.services = services;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "flight_status",
                "Bir ucusun guncel durumunu doner: nereden nereye, planlanan kalkis saati "
                        + "ve ucusun durumu (SCHEDULED, BOARDING, DEPARTED, LANDED, CANCELLED).",
                Map.of("flightNumber", "Ucus numarasi, ornek: ZZ123")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String flightNumber = String.valueOf(arguments.get("flightNumber"));
        Map<String, Object> flight = services.flightStatus(flightNumber);

        if (flight == null) {
            return "%s numarali ucus bulunamadi.".formatted(flightNumber);
        }

        return "Ucus %s: %s -> %s, planlanan kalkis %s, durum %s.".formatted(
                flight.get("flightNumber"),
                flight.get("origin"),
                flight.get("destination"),
                flight.get("scheduledDeparture"),
                flight.get("status"));
    }
}
