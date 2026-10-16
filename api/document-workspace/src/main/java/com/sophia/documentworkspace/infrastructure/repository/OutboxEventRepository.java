package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select e from OutboxEvent e
            where e.publishedAt is null and e.attempts < :maxAttempts
            order by e.createdAt asc
            """)
    List<OutboxEvent> findNextBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    long deleteByPublishedAtBefore(Instant threshold);

    @Query("select count(e) from OutboxEvent e where e.publishedAt is null")
    long countPending();

    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null")
    Instant oldestPendingCreatedAt();
}
