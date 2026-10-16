package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.dto.response.NotificationGroupResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    @Query("""
            SELECT new com.sophia.notification.domain.dto.response.NotificationGroupResponse(
                n.groupKey,
                COUNT(n),
                SUM(CASE WHEN n.readAt IS NULL THEN 1L ELSE 0L END),
                MIN(n.createdAt),
                MAX(n.createdAt))
            FROM Notification n
            WHERE n.recipientId = :recipientId
              AND n.groupKey IS NOT NULL
              AND n.deletedAt IS NULL
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
              AND (n.expiresAt IS NULL OR n.expiresAt > :now)
              AND (:unreadOnly = false OR n.readAt IS NULL)
            GROUP BY n.groupKey
            ORDER BY MAX(n.createdAt) DESC
            """)
    List<NotificationGroupResponse> findGroups(@Param("recipientId") UUID recipientId,
                                               @Param("now") Instant now,
                                               @Param("unreadOnly") boolean unreadOnly,
                                               Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipientId = :recipientId
              AND n.readAt IS NULL
              AND n.deletedAt IS NULL
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
              AND (n.expiresAt IS NULL OR n.expiresAt > :now)
            """)
    long countUnread(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    Optional<Notification> findByIdAndRecipientIdAndDeletedAtIsNull(UUID id, UUID recipientId);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.recipientId = :recipientId
              AND n.readAt IS NULL
              AND n.deletedAt IS NULL
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :readAt)
              AND (n.expiresAt IS NULL OR n.expiresAt > :readAt)
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.recipientId = :recipientId
              AND n.groupKey = :groupKey
              AND n.readAt IS NULL
              AND n.deletedAt IS NULL
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :readAt)
              AND (n.expiresAt IS NULL OR n.expiresAt > :readAt)
            """)
    int markGroupRead(@Param("recipientId") UUID recipientId,
                      @Param("groupKey") String groupKey,
                      @Param("readAt") Instant readAt);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM notifications WHERE id IN (
                SELECT id FROM notifications
                WHERE read_at IS NOT NULL AND read_at < :threshold
                LIMIT :batchSize)
            """)
    int deleteBatchOfReadBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM notifications WHERE id IN (
                SELECT id FROM notifications WHERE created_at < :threshold LIMIT :batchSize)
            """)
    int deleteBatchOfCreatedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM notifications WHERE id IN (
                SELECT id FROM notifications
                WHERE deleted_at IS NOT NULL AND deleted_at < :threshold
                LIMIT :batchSize)
            """)
    int deleteBatchOfSoftDeletedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE notifications SET deleted_at = :now WHERE id IN (
                SELECT id FROM notifications
                WHERE expires_at IS NOT NULL AND expires_at < :now AND deleted_at IS NULL
                LIMIT :batchSize)
            """)
    int softDeleteBatchOfExpired(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientId = :recipientId")
    int deleteAllForRecipient(@Param("recipientId") UUID recipientId);
}
