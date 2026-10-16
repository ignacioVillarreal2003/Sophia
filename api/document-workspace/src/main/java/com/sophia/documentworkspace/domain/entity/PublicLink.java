package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A revocable, optionally expiring read-only link to one artifact. Only the token's hash is stored. */
@Entity
@Table(name = "public_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicLink extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static PublicLink create(UUID workspaceId, UUID artifactId, String tokenHash, UUID createdBy, Instant expiresAt) {
        PublicLink link = new PublicLink();
        link.workspaceId = workspaceId;
        link.artifactId = artifactId;
        link.tokenHash = tokenHash;
        link.createdBy = createdBy;
        link.expiresAt = expiresAt;
        return link;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
