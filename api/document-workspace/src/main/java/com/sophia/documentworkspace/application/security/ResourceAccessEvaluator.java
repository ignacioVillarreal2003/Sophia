package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.application.security.policy.AccessDecision;
import com.sophia.documentworkspace.application.security.policy.AclPolicy;
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
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a member may perform a mode on a resource. There are exactly two sources of access:
 *
 * <ol>
 *   <li><b>Workspace membership</b>: the member role (ADMIN, EDITOR, VIEWER) applies to every item
 *       of the workspace.</li>
 *   <li><b>Direct grants</b>: a member can be given modes on one particular folder or file (and what is
 *       inside a folder), on top of what the role already allows.</li>
 * </ol>
 *
 * Mutating modes are always rejected while the workspace is read-only.
 */
@Service
@RequiredArgsConstructor
public class ResourceAccessEvaluator {

    private final RolePolicy rolePolicy;
    private final AclPolicy aclPolicy;
    private final AclResolver aclResolver;

    /**
     * @param resourceChain resource id followed by ancestor ids (root last), used so a grant on a
     *                      folder reaches what it contains.
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
        if (mode.isMutating() && access.workspaceStatus() == WorkspaceStatus.READ_ONLY) {
            return AccessDecision.deny(ErrorCode.CONFLICT, "This workspace is read-only");
        }

        Set<AccessMode> grantedModes = aclResolver.effectiveModes(access, resourceChain);
        PolicyContext context = new PolicyContext(access, resource, mode, grantedModes);

        AccessDecision role = rolePolicy.evaluate(context);
        if (role.allowed()) {
            return role;
        }
        AccessDecision grant = aclPolicy.evaluate(context);
        if (grant.allowed()) {
            return grant;
        }
        // The role deny explains what is missing.
        return role;
    }
}
