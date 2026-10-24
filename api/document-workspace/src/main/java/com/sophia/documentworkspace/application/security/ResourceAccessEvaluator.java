package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.application.security.policy.AccessDecision;
import com.sophia.documentworkspace.application.security.policy.PolicyContext;
import com.sophia.documentworkspace.application.security.policy.RolePolicy;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.security.SecuredResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Decides whether a member may perform a mode on a resource. The one source of access is workspace membership: the
 * member role (ADMIN, EDITOR, VIEWER) applies to every item of the workspace. Someone who is not a member has no
 * access to anything in it. Mutating modes are always rejected while the workspace is read-only.
 */
@Service
@RequiredArgsConstructor
public class ResourceAccessEvaluator {

    private final RolePolicy rolePolicy;

    /**
     * @param resourceChain resource id followed by ancestor ids (root last); kept for callers that already hold it.
     * @throws ForbiddenOperationException when the access is denied.
     */
    public void require(WorkspaceAccessSnapshot access,
                        SecuredResource resource,
                        AccessMode mode,
                        List<UUID> resourceChain) {
        AccessDecision decision = evaluate(access, resource, mode, resourceChain);
        if (decision.denied()) {
            throw new ForbiddenOperationException(decision.errorCode(), decision.reason());
        }
    }

    public AccessDecision evaluate(WorkspaceAccessSnapshot access,
                                   SecuredResource resource,
                                   AccessMode mode,
                                   List<UUID> resourceChain) {
        if (!resource.getWorkspaceId().equals(access.workspaceId())) {
            return AccessDecision.deny(ErrorCode.ACCESS_DENIED,
                    "The resource does not belong to this workspace");
        }
        if (!access.isMember()) {
            return AccessDecision.deny(ErrorCode.MEMBERSHIP_REQUIRED, "You are not a member of this workspace");
        }
        if (mode.isMutating() && access.workspaceStatus() == WorkspaceStatus.READ_ONLY) {
            return AccessDecision.deny(ErrorCode.CONFLICT, "This workspace is read-only");
        }
        return rolePolicy.evaluate(new PolicyContext(access, resource, mode));
    }
}
