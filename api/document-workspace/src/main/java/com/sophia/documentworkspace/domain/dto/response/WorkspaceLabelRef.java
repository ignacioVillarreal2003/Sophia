package com.sophia.documentworkspace.domain.dto.response;

import java.util.UUID;

/** A personal label as shown on a workspace. */
public record WorkspaceLabelRef(UUID id, String name, String color) {
}
