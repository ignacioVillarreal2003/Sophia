package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** One active collaborator of a document. */
@Schema(description = "One active collaborator currently present in a document")
public record PresenceUser(
        UUID userId,
        String email,
        Instant joinedAt
) {
}
