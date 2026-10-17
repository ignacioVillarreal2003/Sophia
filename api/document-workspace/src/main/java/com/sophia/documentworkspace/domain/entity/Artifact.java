package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.security.SecuredResource;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "artifacts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind", length = 24)
@Getter
public abstract class Artifact extends SoftDeletableEntity implements SecuredResource {

    protected static final String PATH_SEPARATOR = ".";

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Materialized path of the ancestors: their ids, each followed by a dot, root first.
     * Empty for items at the workspace root. The parent folder is the last id.
     */
    @Column(nullable = false, length = 4000)
    private String path;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    private ArtifactType type;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(length = 2000)
    private String description;

    protected Artifact() {
    }

    protected void init(ArtifactType type,
                        UUID workspaceId,
                        Folder parent,
                        String name,
                        UUID ownerId,
                        String description) {
        if (type.kind() != kind()) {
            throw new IllegalArgumentException(
                    "Type %s does not belong to kind %s".formatted(type, kind()));
        }
        this.type = type;
        this.workspaceId = workspaceId;
        this.path = parent == null ? "" : parent.childPath();
        this.name = name;
        this.ownerId = ownerId;
        this.description = description;
    }

    /** The folder this item lives in, derived from the path; null at the workspace root. */
    public UUID getParentId() {
        List<UUID> ancestors = ancestorIds();
        return ancestors.isEmpty() ? null : ancestors.get(ancestors.size() - 1);
    }

    public abstract ArtifactType.Kind kind();

    /** Copy into the same workspace. */
    public Artifact copyWith(String name, Folder targetParent, UUID newOwnerId) {
        return copyTo(getWorkspaceId(), name, targetParent, newOwnerId);
    }

    /** Copy into any workspace; the parent, when given, must belong to that workspace. */
    public abstract Artifact copyTo(UUID targetWorkspaceId, String name, Folder targetParent, UUID newOwnerId);

    public void rename(String name) {
        this.name = name;
    }

    public void describe(String description) {
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    public void moveTo(Folder newParent) {
        this.path = newParent == null ? "" : newParent.childPath();
    }

    public String subtreePrefix() {
        return path + getId() + PATH_SEPARATOR;
    }

    public List<UUID> ancestorIds() {
        List<UUID> ids = new ArrayList<>();
        if (path != null && !path.isEmpty()) {
            for (String part : path.split("\\" + PATH_SEPARATOR)) {
                if (!part.isBlank()) {
                    ids.add(UUID.fromString(part));
                }
            }
        }
        return ids;
    }

    public List<UUID> resourceChain() {
        List<UUID> chain = new ArrayList<>();
        chain.add(getId());
        chain.addAll(ancestorIds());
        return chain;
    }

    public boolean isDescendantOf(Artifact other) {
        return path != null && path.contains(other.getId().toString() + PATH_SEPARATOR);
    }
}
