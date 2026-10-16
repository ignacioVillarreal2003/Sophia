package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.UserEventOutbox;
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
public interface UserEventOutboxRepository extends JpaRepository<UserEventOutbox, UUID> {

    @Query("SELECT e FROM UserEventOutbox e "
            + "WHERE e.publishedAt IS NULL AND e.attempts < :maxAttempts ORDER BY e.occurredAt ASC")
    List<UserEventOutbox> findNextBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UserEventOutbox e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :threshold")
    int deleteAllPublishedBefore(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(e) FROM UserEventOutbox e WHERE e.publishedAt IS NULL")
    long countPending();

    @Query("SELECT MIN(e.occurredAt) FROM UserEventOutbox e WHERE e.publishedAt IS NULL")
    Instant oldestPendingCreatedAt();
}
