package com.sophia.documentworkspace.application.security.policy;

import com.sophia.documentworkspace.domain.exception.ErrorCode;

/**
 * Outcome of one policy evaluation. Mandatory policies (MAC) short-circuit the
 * chain on deny; discretionary policies (role, ownership, ACL) allow when any
 * of them grants the mode.
 */
public record AccessDecision(boolean allowed, ErrorCode errorCode, String reason) {

    private static final AccessDecision ALLOW = new AccessDecision(true, null, null);

    public static AccessDecision allow() {
        return ALLOW;
    }

    public static AccessDecision deny(ErrorCode errorCode, String reason) {
        return new AccessDecision(false, errorCode, reason);
    }

    public boolean denied() {
        return !allowed;
    }
}
