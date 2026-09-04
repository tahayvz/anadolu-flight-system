package com.anadoluair.flight.events;

/**
 * Centralized Kafka topic names used across all services.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class
    }

    // Flight related topics
    public static final String FLIGHT_EVENTS = "anadolu.flight.events";
    public static final String FLIGHT_STATUS_UPDATES = "anadolu.flight.status";

    // Booking related topics
    public static final String BOOKING_EVENTS = "anadolu.booking.events";
    public static final String BOOKING_NOTIFICATIONS = "anadolu.booking.notifications";

    // Integration layer topics (from external systems)
    public static final String EXTERNAL_FLIGHT_FEED = "anadolu.external.flight.feed";
    public static final String EXTERNAL_SCHEDULE_UPDATES = "anadolu.external.schedule.updates";

    // Saga orchestration topics
    public static final String SAGA_COMMANDS = "anadolu.saga.commands";
    public static final String SAGA_REPLIES = "anadolu.saga.replies";
    public static final String SAGA_COMPENSATION = "anadolu.saga.compensation";
}
