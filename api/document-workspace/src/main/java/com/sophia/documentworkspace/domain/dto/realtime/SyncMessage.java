package com.sophia.documentworkspace.domain.dto.realtime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A binary Yjs update, base64-encoded, sent by a client. {@code clientId} is
 * the sender's ephemeral collaboration id so receivers can discard echoes.
 */
@Schema(description = "Binary Yjs document update, base64-encoded, sent by a client")
public record SyncMessage(
        @Schema(description = "Sender's ephemeral collaboration id, used by receivers to discard their own echoed updates")
        String clientId,
        String update
) {
}
