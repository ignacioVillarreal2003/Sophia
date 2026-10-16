package com.sophia.documentworkspace.domain.dto.response;

import java.time.Instant;

/** What an anonymous visitor of a public link gets to see. */
public record PublicViewResponse(
        String name,
        String type,
        String workspaceName,
        Instant updatedAt,
        String markdown
) {
}
