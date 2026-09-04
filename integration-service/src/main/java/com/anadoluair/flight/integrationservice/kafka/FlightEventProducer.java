package com.anadoluair.flight.integrationservice.kafka;

import com.anadoluair.flight.events.FlightEvent;
import com.anadoluair.flight.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer for publishing flight events.
 * Used by SOAP endpoints to send events to downstream services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightEventProducer {

    private final KafkaTemplate<String, FlightEvent> kafkaTemplate;

    /**
     * Publish a flight event to Kafka.
     * Uses the flight number as the partition key for ordering guarantees.
     *
     * @param event The flight event to publish
     */
    public void publishFlightEvent(FlightEvent event) {
        String key = event.getFlightData().getFlightNumber();

        log.info("Publishing flight event: type={}, flightNumber={}, eventId={}",
                event.getEventType(), key, event.getEventId());

        CompletableFuture<SendResult<String, FlightEvent>> future =
                kafkaTemplate.send(KafkaTopics.FLIGHT_EVENTS, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish flight event: {}", event.getEventId(), ex);
            } else {
                log.debug("Flight event published successfully: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish a flight status update event.
     * Sent to a separate topic for real-time status tracking.
     *
     * @param event The status update event
     */
    public void publishStatusUpdate(FlightEvent event) {
        String key = event.getFlightData().getFlightNumber();

        log.info("Publishing status update: flightNumber={}, status={}",
                key, event.getFlightData().getStatus());

        kafkaTemplate.send(KafkaTopics.FLIGHT_STATUS_UPDATES, key, event);
    }
}
