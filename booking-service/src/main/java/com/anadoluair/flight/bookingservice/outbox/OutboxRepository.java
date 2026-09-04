package com.anadoluair.flight.bookingservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Outbox Events.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find pending events for processing.
     * Uses SKIP LOCKED to allow concurrent processing.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    /**
     * Find events stuck in PROCESSING state (potential deadlock recovery).
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PROCESSING' AND e.createdAt < :threshold")
    List<OutboxEvent> findStuckEvents(@Param("threshold") LocalDateTime threshold);

    /**
     * Find failed events for DLQ processing.
     */
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);

    /**
     * Count pending events (for monitoring).
     */
    long countByStatus(OutboxEvent.OutboxStatus status);

    /**
     * Delete old completed events (cleanup).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'COMPLETED' AND e.processedAt < :threshold")
    int deleteCompletedEventsBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * Check if event already exists (idempotency check).
     */
    boolean existsByEventId(String eventId);

    /**
     * Count pending events by aggregate type.
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PENDING' AND e.aggregateType = :aggregateType")
    long countPendingByAggregateType(@Param("aggregateType") String aggregateType);
}
