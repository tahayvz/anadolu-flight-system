package com.anadoluair.flight.bookingservice.kafka;

import com.anadoluair.flight.events.BookingEvent;
import com.anadoluair.flight.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer for publishing booking events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;

    /**
     * Publish a booking event to Kafka.
     * Uses the booking reference (PNR) as the partition key.
     */
    public void publishBookingEvent(BookingEvent event) {
        String key = event.getBookingData().getBookingReference();

        log.info("Publishing booking event: type={}, pnr={}, eventId={}", event.getEventType(), key, event.getEventId());

        kafkaTemplate.send(KafkaTopics.BOOKING_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish booking event: {}", event.getEventId(), ex);
                    } else {
                        log.debug("Booking event published: partition={}, offset={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
