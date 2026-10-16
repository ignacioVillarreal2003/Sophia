package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of a document's text content at a given content version. Quick successive saves by the
 * same author are coalesced into one snapshot; a snapshot can be given a name to become a checkpoint.
 */
@Entity
@Table(name = "document_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_document_versions_seq", columnNames = {"artifact_id", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion extends BaseEntity {

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private long version;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 120)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public static DocumentVersion of(UUID artifactId, UUID workspaceId, long version, String content,
                                     UUID createdBy) {
        DocumentVersion snapshot = new DocumentVersion();
        snapshot.artifactId = artifactId;
        snapshot.workspaceId = workspaceId;
        snapshot.version = version;
        snapshot.content = content;
        snapshot.createdBy = createdBy;
        snapshot.savedAt = Instant.now();
        return snapshot;
    }

    /** Folds a newer save by the same author into this snapshot. */
    public void absorb(long newVersion, String newContent) {
        this.version = newVersion;
        this.content = newContent;
        this.savedAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean isNamed() {
        return name != null && !name.isBlank();
    }
}
