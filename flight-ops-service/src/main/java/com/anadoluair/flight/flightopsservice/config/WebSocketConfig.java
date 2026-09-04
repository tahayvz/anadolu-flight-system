package com.anadoluair.flight.flightopsservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for real-time flight updates.
 *
 * Clients connect to: ws://localhost:8082/ws-flight
 *
 * Subscribe to topics:
 *   - /topic/flights                    -> All flight updates
 *   - /topic/flight/{id}               -> Specific flight updates
 *   - /topic/bookings                  -> All booking updates
 *   - /topic/flight/{id}/bookings      -> Flight-specific booking updates
 *   - /topic/status                    -> Status board updates
 *
 * Send messages:
 *   - /app/subscribe       -> Subscribe to flight updates
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for topics and queues
        // /topic - broadcast to all subscribers
        // /queue - point-to-point messaging
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages FROM client TO server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint - clients connect here
        registry.addEndpoint("/ws-flight")
                .setAllowedOriginPatterns("*")  // Allow all origins (configure in production)
                .withSockJS();  // Enable SockJS fallback for browsers without WebSocket support

        // Raw WebSocket endpoint (without SockJS)
        registry.addEndpoint("/ws-flight")
                .setAllowedOriginPatterns("*");
    }
}
