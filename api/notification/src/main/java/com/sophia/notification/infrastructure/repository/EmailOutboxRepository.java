package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.EmailOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    @Query("""
            SELECT m.id FROM EmailOutboxMessage m
            WHERE m.publishedAt IS NULL AND m.nextAttemptAt <= :now
            ORDER BY m.nextAttemptAt ASC
            """)
    List<UUID> findNextBatchIds(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM email_outbox WHERE id IN (
                SELECT id FROM email_outbox
                WHERE published_at IS NOT NULL AND published_at < :threshold
                LIMIT :batchSize)
            """)
    int deleteBatchOfCompletedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);

    @Query("SELECT COUNT(m) FROM EmailOutboxMessage m WHERE m.publishedAt IS NULL")
    long countPending();

    @Query("SELECT MIN(m.createdAt) FROM EmailOutboxMessage m WHERE m.publishedAt IS NULL")
    Instant oldestPendingCreatedAt();
}
