package com.anadoluair.flight.bookingservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) Configuration for Booking Service.
 * Access Swagger UI at: http://localhost:8083/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Anadolu Air Booking Service API")
                        .description("""
                                Flight Booking Management API for Anadolu Air inspired system.

                                ## Features
                                - Create and manage flight bookings
                                - PNR (Passenger Name Record) generation
                                - Online check-in
                                - Booking cancellation

                                ## Architecture
                                This service is part of an event-driven microservices architecture:
                                - Publishes booking events to Kafka
                                - Uses H2 in-memory database (demo) / PostgreSQL (production)
                                - Integrated with flight-ops-service via Kafka
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Anadolu Air Flight System")
                                .email("dev@anadolu-flight-system.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local Development"),
                        new Server().url("http://booking-service:8083").description("Docker Environment")
                ));
    }
}
