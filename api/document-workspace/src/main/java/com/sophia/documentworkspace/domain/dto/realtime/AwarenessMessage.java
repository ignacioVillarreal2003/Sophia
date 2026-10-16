package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Ephemeral collaboration state: cursor position, selection, user color...
 * Relayed to collaborators, never persisted. The payload follows the Yjs
 * awareness protocol (base64) or a plain JSON map — the server does not
 * interpret it.
 */
@Schema(description = "Ephemeral collaboration state (cursor position, selection, user color) relayed "
        + "to collaborators but never persisted")
public record AwarenessMessage(
        String clientId,
        @Schema(description = "Yjs awareness protocol payload (base64) or a plain JSON map; the server does not interpret it")
        String payload,
        Map<String, Object> cursor
) {
}
