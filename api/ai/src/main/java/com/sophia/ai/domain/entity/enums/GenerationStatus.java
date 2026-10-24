package com.sophia.ai.domain.entity.enums;

public enum GenerationStatus {
    SUCCESS,
    FAILED,
    /** The model declined the request. */
    REFUSED,
    /** The user closed the stream before it ended. */
    CANCELLED
}
