package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.NotificationDispatch;
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
public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, UUID> {

    List<NotificationDispatch> findAllByNotificationIdOrderByCreatedAtDesc(UUID notificationId);

    List<NotificationDispatch> findAllByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM notification_dispatches WHERE id IN (
                SELECT id FROM notification_dispatches
                WHERE (sent_at IS NOT NULL AND sent_at < :threshold)
                   OR (failed_at IS NOT NULL AND failed_at < :threshold)
                LIMIT :batchSize)
            """)
    int deleteBatchOfTerminalBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
