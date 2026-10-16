package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Broadcast when a collaborator joins or leaves a document. */
@Schema(description = "Broadcast when a collaborator joins or leaves a document")
public record PresenceEvent(
        Type type,
        PresenceUser user
) {

    public enum Type {
        JOINED,
        LEFT
    }
}
