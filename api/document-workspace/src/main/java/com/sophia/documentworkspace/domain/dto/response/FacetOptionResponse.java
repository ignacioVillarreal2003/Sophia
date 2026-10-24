package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One value a person can pick to narrow a listing, with the text to show for it. */
@Schema(description = "A value to filter by and its label")
public record FacetOptionResponse(String value, String label) {
}
