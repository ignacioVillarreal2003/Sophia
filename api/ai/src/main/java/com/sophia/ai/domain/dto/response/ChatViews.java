package com.sophia.ai.domain.dto.response;

import com.sophia.ai.domain.entity.ChatMessage.Citation;
import com.sophia.ai.domain.entity.Conversation.SourceEntry;
import com.sophia.ai.domain.model.Enums.ChatMode;
import com.sophia.ai.domain.model.Enums.Stance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** How conversations are shown to the client. */
public final class ChatViews {

    private ChatViews() {
    }

    public record ConversationSummary(UUID id, UUID workspaceId, ChatMode mode, String title, String topic,
                                      Stance stance, String language, List<SourceEntry> sources, Instant createdAt,
                                      Instant updatedAt) {
    }

    public record MessageView(UUID id, String role, String content, List<Citation> citations, Instant createdAt) {
    }

    public record ConversationView(ConversationSummary conversation, List<MessageView> messages) {
    }
}
