package com.sophia.documentworkspace.application.security.policy;

import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.security.SecuredResource;

/**
 * Everything a policy needs to evaluate one access attempt: the subject's workspace access snapshot, the target
 * resource and the attempted mode.
 */
public record PolicyContext(
        WorkspaceAccessSnapshot access,
        SecuredResource resource,
        AccessMode mode
) {
}
