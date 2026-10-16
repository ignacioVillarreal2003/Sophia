package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.util.UUID;

/**
 * Text-content document: a Markdown note, a checklist (Markdown task list) or a
 * spreadsheet (workbook JSON). The raw text lives in {@code content}; every saved
 * change bumps {@code contentVersion} and is recorded in {@code document_versions}.
 */
@Entity
@DiscriminatorValue("DOCUMENT")
@Getter
public class DocumentArtifact extends Artifact {

    /** Monotonic content version, bumped on every saved change. */
    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    @Column(columnDefinition = "text")
    private String content;

    protected DocumentArtifact() {
    }

    public static DocumentArtifact create(ArtifactType type,
                                          UUID workspaceId,
                                          Folder parent,
                                          String name,
                                          UUID ownerId,
                                          String description,
                                          String content) {
        DocumentArtifact document = new DocumentArtifact();
        document.init(type, workspaceId, parent, name, ownerId, description);
        document.content = content;
        document.contentVersion = 0;
        return document;
    }

    public long bumpContentVersion() {
        return ++contentVersion;
    }

    /** Replaces the text content and returns the new content version. */
    public long replaceContent(String newContent) {
        this.content = newContent;
        return ++contentVersion;
    }

    public String contentOrEmpty() {
        return content == null ? "" : content;
    }

    @Override
    public ArtifactType.Kind kind() {
        return ArtifactType.Kind.DOCUMENT;
    }

    @Override
    public Artifact copyWith(String name, Folder targetParent, UUID newOwnerId) {
        return create(getType(), getWorkspaceId(), targetParent, name, newOwnerId, getDescription(), content);
    }
}
