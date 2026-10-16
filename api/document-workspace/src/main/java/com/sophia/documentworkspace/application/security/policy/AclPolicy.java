package com.sophia.documentworkspace.application.security.policy;

import com.sophia.documentworkspace.domain.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Discretionary ACL policy: allows the mode when an active ACL entry
 * (on the resource itself or inherited from an ancestor folder) grants it to
 * the subject. The effective modes are resolved
 * by the AclResolver before evaluation and travel in the context.
 */
@Component
public class AclPolicy implements ResourcePolicy {

    @Override
    public AccessDecision evaluate(PolicyContext context) {
        if (context.aclModes() != null && context.aclModes().contains(context.mode())) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny(ErrorCode.ACCESS_DENIED,
                "No ACL entry grants " + context.mode() + " on this resource");
    }
}
