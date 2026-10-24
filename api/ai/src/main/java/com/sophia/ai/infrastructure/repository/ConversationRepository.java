package com.sophia.ai.infrastructure.repository;

import com.sophia.ai.domain.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("select c from Conversation c where c.userId = :userId and (:workspaceId is null or c.workspaceId = :workspaceId)"
            + " order by c.updatedAt desc")
    List<Conversation> recent(UUID userId, UUID workspaceId, Pageable page);

    @Modifying
    @Transactional
    @Query("update Conversation c set c.updatedAt = :now where c.id = :id")
    int touch(UUID id, Instant now);

    @Modifying
    @Transactional
    @Query("update Conversation c set c.title = :title where c.id = :id")
    int retitle(UUID id, String title);

    @Modifying
    @Transactional
    @Query("delete from Conversation c where c.userId = :userId")
    int deleteAllOf(UUID userId);

    @Modifying
    @Query("delete from Conversation c where c.updatedAt < :cutoff")
    int deleteIdleSince(Instant cutoff);
}
