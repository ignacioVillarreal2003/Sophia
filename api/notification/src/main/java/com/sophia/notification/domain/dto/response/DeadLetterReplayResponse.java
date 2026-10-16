package com.sophia.notification.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome of a dead letter replay run")
public record DeadLetterReplayResponse(
        @Schema(description = "Dead letter topic the records were read from")
        String topic,
        @Schema(description = "Number of records republished onto the originating topic")
        int replayed) {
}
