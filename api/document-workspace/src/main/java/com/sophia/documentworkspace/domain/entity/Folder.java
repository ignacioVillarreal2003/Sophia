package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.util.UUID;

/** Container node of the hierarchy: folders hold folders and any other artifact. */
@Entity
@DiscriminatorValue("FOLDER")
@lombok.Getter
public class Folder extends Artifact {

    /** Accent color shown on the folder (a hex value such as #6366F1). */
    @Column(length = 16)
    private String color;

    protected Folder() {
    }

    public static Folder create(UUID workspaceId,
                                Folder parent,
                                String name,
                                UUID ownerId,
                                String description,
                                String color) {
        Folder folder = new Folder();
        folder.init(ArtifactType.FOLDER, workspaceId, parent, name, ownerId, description);
        folder.color = color;
        return folder;
    }

    public void changeColor(String color) {
        this.color = color == null || color.isBlank() ? null : color;
    }

    /** Path every direct child of this folder carries. */
    public String childPath() {
        return subtreePrefix();
    }

    @Override
    public ArtifactType.Kind kind() {
        return ArtifactType.Kind.FOLDER;
    }

    @Override
    public Artifact copyTo(UUID targetWorkspaceId, String name, Folder targetParent, UUID newOwnerId) {
        return create(targetWorkspaceId, targetParent, name, newOwnerId, getDescription(), color);
    }
}
