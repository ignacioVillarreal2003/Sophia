package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "acl_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AclEntry extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "subject_user_id", nullable = false)
    private UUID subjectUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Set<AccessMode> modes;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public static AclEntry forUser(UUID workspaceId, UUID resourceId, UUID subjectUserId,
                                   Set<AccessMode> modes, UUID grantedBy, Instant expiresAt) {
        AclEntry entry = base(workspaceId, resourceId, modes, grantedBy, expiresAt);
        entry.subjectUserId = subjectUserId;
        return entry;
    }

    private static AclEntry base(UUID workspaceId, UUID resourceId, Set<AccessMode> modes,
                                 UUID grantedBy, Instant expiresAt) {
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException("An ACL entry must grant at least one access mode");
        }
        AclEntry entry = new AclEntry();
        entry.workspaceId = workspaceId;
        entry.resourceId = resourceId;
        entry.modes = Set.copyOf(modes);
        entry.grantedBy = grantedBy;
        entry.expiresAt = expiresAt;
        return entry;
    }

    public void updateModes(Set<AccessMode> modes) {
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException("An ACL entry must grant at least one access mode");
        }
        this.modes = Set.copyOf(modes);
    }

    public boolean isActive(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public boolean appliesTo(UUID userId) {
        return subjectUserId.equals(userId);
    }
}
