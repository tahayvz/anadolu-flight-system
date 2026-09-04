package com.anadoluair.flight.bookingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Distributed Tracing Configuration.
 * Enables automatic trace propagation across services using B3 headers.
 *
 * Trace context headers (auto-propagated by Micrometer Tracing):
 * - X-B3-TraceId: 128-bit unique trace ID
 * - X-B3-SpanId: 64-bit unique span ID
 * - X-B3-ParentSpanId: Parent span reference
 * - X-B3-Sampled: 1 = sampled, 0 = not sampled
 *
 * Spring Boot 3.2+ auto-configures tracing for RestTemplate/WebClient
 * when micrometer-tracing-bridge-brave is on the classpath.
 */
@Configuration
public class TracingConfig {

    /**
     * RestTemplate bean.
     * Tracing is auto-configured by Spring Boot Actuator.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
