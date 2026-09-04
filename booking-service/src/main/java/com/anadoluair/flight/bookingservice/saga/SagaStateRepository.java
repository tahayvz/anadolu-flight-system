package com.anadoluair.flight.bookingservice.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based repository for Saga state persistence.
 *
 * Key patterns:
 * - saga:{sagaId} -> Saga state JSON
 * - saga:pnr:{pnr} -> sagaId (for lookup by PNR)
 * - saga:flight:{flightNumber}:{date} -> Set of sagaIds
 * - saga:active -> Set of active saga IDs
 * - saga:failed -> Set of failed saga IDs
 *
 * TTL: 24 hours for completed/failed sagas, indefinite for active
 */
@Slf4j
@Repository
public class SagaStateRepository {

    private static final String SAGA_KEY_PREFIX = "saga:";
    private static final String PNR_INDEX_PREFIX = "saga:pnr:";
    private static final String FLIGHT_INDEX_PREFIX = "saga:flight:";
    private static final String ACTIVE_SAGAS_KEY = "saga:active";
    private static final String FAILED_SAGAS_KEY = "saga:failed";
    private static final String COMPENSATING_SAGAS_KEY = "saga:compensating";

    private static final Duration COMPLETED_SAGA_TTL = Duration.ofHours(24);
    private static final Duration ACTIVE_SAGA_TTL = Duration.ofHours(1); // Safety TTL

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public SagaStateRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Save saga state to Redis.
     */
    public void save(BookingSagaState state) {
        String sagaKey = SAGA_KEY_PREFIX + state.getSagaId();

        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(sagaKey, json);

            // Set TTL based on status
            if (isTerminalState(state.getStatus())) {
                redisTemplate.expire(sagaKey, COMPLETED_SAGA_TTL);
                // Remove from active set
                redisTemplate.opsForSet().remove(ACTIVE_SAGAS_KEY, state.getSagaId());

                if (state.getStatus() == BookingSagaState.SagaStatus.FAILED ||
                        state.getStatus() == BookingSagaState.SagaStatus.ROLLED_BACK) {
                    redisTemplate.opsForSet().add(FAILED_SAGAS_KEY, state.getSagaId());
                }
            } else {
                redisTemplate.expire(sagaKey, ACTIVE_SAGA_TTL);
                redisTemplate.opsForSet().add(ACTIVE_SAGAS_KEY, state.getSagaId());

                if (state.getStatus() == BookingSagaState.SagaStatus.COMPENSATING) {
                    redisTemplate.opsForSet().add(COMPENSATING_SAGAS_KEY, state.getSagaId());
                }
            }

            // Index by PNR if available
            if (state.getBookingReference() != null) {
                String pnrKey = PNR_INDEX_PREFIX + state.getBookingReference();
                redisTemplate.opsForValue().set(pnrKey, state.getSagaId());
                redisTemplate.expire(pnrKey, COMPLETED_SAGA_TTL);
            }

            // Index by flight
            if (state.getFlightNumber() != null && state.getFlightDate() != null) {
                String flightKey = FLIGHT_INDEX_PREFIX + state.getFlightNumber() + ":" + state.getFlightDate();
                redisTemplate.opsForSet().add(flightKey, state.getSagaId());
                redisTemplate.expire(flightKey, COMPLETED_SAGA_TTL);
            }

            log.debug("Saved saga state: {}", state.toSummary());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize saga state: {}", state.getSagaId(), e);
            throw new RuntimeException("Failed to save saga state", e);
        }
    }

    /**
     * Find saga by ID.
     */
    public Optional<BookingSagaState> findById(String sagaId) {
        String sagaKey = SAGA_KEY_PREFIX + sagaId;
        Object json = redisTemplate.opsForValue().get(sagaKey);

        if (json == null) {
            return Optional.empty();
        }

        try {
            BookingSagaState state = objectMapper.readValue(json.toString(), BookingSagaState.class);
            return Optional.of(state);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize saga state: {}", sagaId, e);
            return Optional.empty();
        }
    }

    /**
     * Find saga by PNR (booking reference).
     */
    public Optional<BookingSagaState> findByPNR(String pnr) {
        String pnrKey = PNR_INDEX_PREFIX + pnr;
        Object sagaId = redisTemplate.opsForValue().get(pnrKey);

        if (sagaId == null) {
            return Optional.empty();
        }

        return findById(sagaId.toString());
    }

    /**
     * Find all sagas for a flight.
     */
    public List<BookingSagaState> findByFlight(String flightNumber, String flightDate) {
        String flightKey = FLIGHT_INDEX_PREFIX + flightNumber + ":" + flightDate;
        Set<Object> sagaIds = redisTemplate.opsForSet().members(flightKey);

        if (sagaIds == null || sagaIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sagaIds.stream()
                .map(id -> findById(id.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Find all active (non-terminal) sagas.
     */
    public List<BookingSagaState> findActiveSagas() {
        Set<Object> sagaIds = redisTemplate.opsForSet().members(ACTIVE_SAGAS_KEY);

        if (sagaIds == null || sagaIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sagaIds.stream()
                .map(id -> findById(id.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(s -> !isTerminalState(s.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Find all sagas in COMPENSATING state.
     */
    public List<BookingSagaState> findCompensatingSagas() {
        Set<Object> sagaIds = redisTemplate.opsForSet().members(COMPENSATING_SAGAS_KEY);

        if (sagaIds == null || sagaIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sagaIds.stream()
                .map(id -> findById(id.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(s -> s.getStatus() == BookingSagaState.SagaStatus.COMPENSATING)
                .collect(Collectors.toList());
    }

    /**
     * Find timed out sagas for recovery.
     */
    public List<BookingSagaState> findTimedOutSagas() {
        return findActiveSagas().stream()
                .filter(BookingSagaState::isTimedOut)
                .collect(Collectors.toList());
    }

    /**
     * Delete saga state.
     */
    public void delete(String sagaId) {
        Optional<BookingSagaState> stateOpt = findById(sagaId);
        if (stateOpt.isEmpty()) {
            return;
        }

        BookingSagaState state = stateOpt.get();

        // Delete main key
        redisTemplate.delete(SAGA_KEY_PREFIX + sagaId);

        // Remove from indices
        redisTemplate.opsForSet().remove(ACTIVE_SAGAS_KEY, sagaId);
        redisTemplate.opsForSet().remove(FAILED_SAGAS_KEY, sagaId);
        redisTemplate.opsForSet().remove(COMPENSATING_SAGAS_KEY, sagaId);

        if (state.getBookingReference() != null) {
            redisTemplate.delete(PNR_INDEX_PREFIX + state.getBookingReference());
        }

        if (state.getFlightNumber() != null && state.getFlightDate() != null) {
            String flightKey = FLIGHT_INDEX_PREFIX + state.getFlightNumber() + ":" + state.getFlightDate();
            redisTemplate.opsForSet().remove(flightKey, sagaId);
        }

        log.info("Deleted saga state: {}", sagaId);
    }

    /**
     * Get statistics for monitoring.
     */
    public SagaStats getStats() {
        Long activeCount = redisTemplate.opsForSet().size(ACTIVE_SAGAS_KEY);
        Long failedCount = redisTemplate.opsForSet().size(FAILED_SAGAS_KEY);
        Long compensatingCount = redisTemplate.opsForSet().size(COMPENSATING_SAGAS_KEY);

        return new SagaStats(
                activeCount != null ? activeCount : 0,
                failedCount != null ? failedCount : 0,
                compensatingCount != null ? compensatingCount : 0
        );
    }

    private boolean isTerminalState(BookingSagaState.SagaStatus status) {
        return status == BookingSagaState.SagaStatus.COMPLETED ||
                status == BookingSagaState.SagaStatus.FAILED ||
                status == BookingSagaState.SagaStatus.ROLLED_BACK;
    }

    public record SagaStats(long active, long failed, long compensating) {}
}
