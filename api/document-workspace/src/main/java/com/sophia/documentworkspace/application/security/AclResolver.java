package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.AclEntry;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.infrastructure.repository.AclEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the effective ACL modes a member holds on a resource: the union of
 * every active grant made to them, on the
 * resource itself and on every ancestor in the provided chain (inheritance).
 */
@Component
@RequiredArgsConstructor
public class AclResolver {

    private final AclEntryRepository aclEntryRepository;

    /**
     * @param resourceChain the resource id followed by its ancestor ids, root last.
     */
    @Transactional(readOnly = true)
    public Set<AccessMode> effectiveModes(WorkspaceAccessSnapshot access, Collection<UUID> resourceChain) {
        if (resourceChain.isEmpty()) {
            return Set.of();
        }
        List<AclEntry> entries = aclEntryRepository.findAllByResourceIdIn(resourceChain);
        if (entries.isEmpty()) {
            return Set.of();
        }
        Instant now = Instant.now();
        Set<AccessMode> modes = EnumSet.noneOf(AccessMode.class);
        for (AclEntry entry : entries) {
            if (entry.isActive(now)
                    && entry.getWorkspaceId().equals(access.workspaceId())
                    && entry.appliesTo(access.userId())) {
                modes.addAll(entry.getModes());
            }
        }
        return modes;
    }
}
