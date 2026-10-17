package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.util.UUID;

/**
 * Any uploaded binary (PDF, Office, media, ZIP, arbitrary files). Only
 * metadata lives in PostgreSQL; the bytes are in object storage under
 * {@code storageKey}, versioned by the upload service.
 */
@Entity
@DiscriminatorValue("FILE")
@Getter
public class FileArtifact extends Artifact {

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "storage_key", length = 1000)
    private String storageKey;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    /** Plain text extracted from Office documents and PDFs, used for search. */
    @Column(name = "preview_text", columnDefinition = "text")
    private String previewText;

    @Column(name = "thumbnail_storage_key", length = 1000)
    private String thumbnailStorageKey;

    @Column(name = "preview_storage_key", length = 1000)
    private String previewStorageKey;

    protected FileArtifact() {
    }

    public static FileArtifact create(ArtifactType type,
                                      UUID workspaceId,
                                      Folder parent,
                                      String name,
                                      UUID ownerId,
                                      String mimeType,
                                      Long sizeBytes,
                                      String storageKey,
                                      String checksumSha256,
                                      String originalFilename) {
        FileArtifact file = new FileArtifact();
        file.init(type, workspaceId, parent, name, ownerId, null);
        file.mimeType = mimeType;
        file.sizeBytes = sizeBytes;
        file.storageKey = storageKey;
        file.checksumSha256 = checksumSha256;
        file.originalFilename = originalFilename;
        return file;
    }

    /** Points the artifact at a newly uploaded version of its content. */
    public void attachContent(String storageKey, String mimeType, Long sizeBytes, String checksumSha256) {
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
    }

    /** Stores what the preview generator produced; null arguments leave the previous value. */
    public void attachPreview(String text, String thumbnailKey, String previewKey) {
        if (text != null) {
            this.previewText = text;
        }
        if (thumbnailKey != null) {
            this.thumbnailStorageKey = thumbnailKey;
        }
        if (previewKey != null) {
            this.previewStorageKey = previewKey;
        }
    }

    @Override
    public ArtifactType.Kind kind() {
        return ArtifactType.Kind.FILE;
    }

    @Override
    public Artifact copyTo(UUID targetWorkspaceId, String name, Folder targetParent, UUID newOwnerId) {
        // The copy references the same immutable storage object; uploads never
        // mutate a key in place, so sharing it between artifacts is safe.
        return create(getType(), targetWorkspaceId, targetParent, name, newOwnerId,
                mimeType, sizeBytes, storageKey, checksumSha256, originalFilename);
    }
}
