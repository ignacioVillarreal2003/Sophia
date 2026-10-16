package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One content version of a file artifact. Storage objects are immutable —
 * every new upload gets a fresh key — so versions simply point at their key
 * and restoring a version swaps the artifact's current pointer.
 */
@Entity
@Table(name = "file_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_file_versions_artifact_seq", columnNames = {"artifact_id", "seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileVersion extends BaseEntity {

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private long seq;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    public static FileVersion of(UUID artifactId, UUID workspaceId, long seq, String storageKey,
                                 String mimeType, Long sizeBytes, String checksumSha256,
                                 UUID uploadedBy) {
        FileVersion version = new FileVersion();
        version.artifactId = artifactId;
        version.workspaceId = workspaceId;
        version.seq = seq;
        version.storageKey = storageKey;
        version.mimeType = mimeType;
        version.sizeBytes = sizeBytes;
        version.checksumSha256 = checksumSha256;
        version.uploadedBy = uploadedBy;
        return version;
    }
}
