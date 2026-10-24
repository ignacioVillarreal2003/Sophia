package com.sophia.ai.infrastructure.repository;

import com.sophia.ai.domain.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    long countByConversationId(UUID conversationId);

    /** Newest first; the caller reverses it. */
    @Query("select m from ChatMessage m where m.conversationId = :conversationId order by m.createdAt desc")
    List<ChatMessage> latest(UUID conversationId, Pageable page);
}
