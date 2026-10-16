package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.NotificationCommandOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationCommandOutboxRepository
        extends JpaRepository<NotificationCommandOutboxMessage, UUID> {

    @Query("""
            SELECT m FROM NotificationCommandOutboxMessage m
            WHERE m.publishedAt IS NULL AND m.attempts < :maxAttempts
            ORDER BY m.occurredAt ASC
            """)
    List<NotificationCommandOutboxMessage> findNextBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM NotificationCommandOutboxMessage m
            WHERE m.publishedAt IS NOT NULL AND m.publishedAt < :threshold
            """)
    int deleteAllPublishedBefore(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(m) FROM NotificationCommandOutboxMessage m WHERE m.publishedAt IS NULL")
    long countPending();

    @Query("SELECT MIN(m.occurredAt) FROM NotificationCommandOutboxMessage m WHERE m.publishedAt IS NULL")
    Instant oldestPendingCreatedAt();
}
