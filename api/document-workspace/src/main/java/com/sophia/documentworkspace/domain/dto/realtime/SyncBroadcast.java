package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Server-relayed Yjs update with the authenticated author attached. */
@Schema(description = "Server-relayed Yjs update broadcast to collaborators, with the authenticated author attached")
public record SyncBroadcast(
        String clientId,
        String update,
        UUID actorId
) {
}
