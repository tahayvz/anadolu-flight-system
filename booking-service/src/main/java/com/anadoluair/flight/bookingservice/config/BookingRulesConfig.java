package com.anadoluair.flight.bookingservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for booking business rules.
 * All values are configurable via application.yml under booking.rules.*
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "booking.rules")
public class BookingRulesConfig {

    /**
     * Maximum passengers per booking (default: 9)
     */
    private int maxPassengers = 9;

    /**
     * Check-in opens X hours before departure (default: 24)
     */
    private int checkinOpenHours = 24;

    /**
     * Check-in closes X hours before departure (default: 1)
     */
    private int checkinCloseHours = 1;

    /**
     * Minimum hours before departure to create a booking (default: 2)
     */
    private int minBookingHours = 2;

    /**
     * Infant must be accompanied by an adult (default: true)
     */
    private boolean infantRequiresAdult = true;

    /**
     * Maximum infants per adult (default: 1)
     */
    private int maxInfantsPerAdult = 1;

    /**
     * Child age range: minimum age (default: 2)
     */
    private int childMinAge = 2;

    /**
     * Child age range: maximum age (default: 11)
     */
    private int childMaxAge = 11;

    /**
     * Infant maximum age (default: 2)
     */
    private int infantMaxAge = 2;
}
