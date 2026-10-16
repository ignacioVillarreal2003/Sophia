package com.sophia.documentworkspace.application.security.policy;

import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.security.SecuredResource;

import java.util.Set;

/**
 * Everything a policy needs to evaluate one access attempt:
 * the subject's workspace access snapshot, the target resource, the attempted
 * mode, and the effective ACL modes already resolved for this subject
 * (own entries plus entries inherited from ancestor folders).
 */
public record PolicyContext(
        WorkspaceAccessSnapshot access,
        SecuredResource resource,
        AccessMode mode,
        Set<AccessMode> aclModes
) {
}
