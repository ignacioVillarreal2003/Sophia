package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.util.UUID;

/**
 * Reusable template. {@code templateContent} holds a snapshot of the source
 * artifact's structure (blocks for documents, columns for tables, subtree for
 * folders); instantiating a template materializes a fresh artifact from it.
 * Templates are versioned (every re-snapshot bumps {@code templateVersion})
 * and can be published to make them visible to every workspace member.
 */
@Entity
@DiscriminatorValue("TEMPLATE")
@Getter
public class TemplateArtifact extends Artifact {

    /** Artifact type this template produces when instantiated. */
    @Enumerated(EnumType.STRING)
    @Column(name = "template_target_type", length = 32)
    private ArtifactType targetType;

    /** Snapshot of the source document text (Markdown or workbook JSON); empty for folder templates. */
    @Column(name = "template_content", columnDefinition = "text")
    private String templateContent;

    @Column(name = "template_published")
    private Boolean published;

    @Column(name = "template_version")
    private Long templateVersion;

    protected TemplateArtifact() {
    }

    public static TemplateArtifact create(UUID workspaceId,
                                          Folder parent,
                                          String name,
                                          UUID ownerId,
                                          ArtifactType targetType,
                                          String description,
                                          String templateContent) {
        TemplateArtifact template = new TemplateArtifact();
        template.init(ArtifactType.TEMPLATE, workspaceId, parent, name, ownerId, description);
        template.targetType = targetType;
        template.templateContent = templateContent;
        template.published = Boolean.FALSE;
        template.templateVersion = 1L;
        return template;
    }

    /** Replaces the snapshot and bumps the template version. */
    public void resnapshot(String newContent) {
        this.templateContent = newContent;
        this.templateVersion = (templateVersion == null ? 1L : templateVersion) + 1;
    }

    public void publish() {
        this.published = Boolean.TRUE;
    }

    public void unpublish() {
        this.published = Boolean.FALSE;
    }

    public boolean isPublished() {
        return Boolean.TRUE.equals(published);
    }

    @Override
    public ArtifactType.Kind kind() {
        return ArtifactType.Kind.TEMPLATE;
    }

    @Override
    public Artifact copyWith(String name, Folder targetParent, UUID newOwnerId) {
        return create(getWorkspaceId(), targetParent, name, newOwnerId, targetType,
                getDescription(), templateContent);
    }
}
