package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/** Server-relayed awareness state with the authenticated author attached. */
@Schema(description = "Server-relayed awareness state broadcast to collaborators, with the authenticated author attached")
public record AwarenessBroadcast(
        String clientId,
        @Schema(description = "Yjs awareness protocol payload (base64) or a plain JSON map; the server does not interpret it")
        String payload,
        Map<String, Object> cursor,
        UUID actorId,
        String actorEmail
) {
}
