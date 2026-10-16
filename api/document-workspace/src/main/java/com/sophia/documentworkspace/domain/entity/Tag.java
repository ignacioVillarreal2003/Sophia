package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Workspace-scoped tag; tags can nest through {@code parentTagId}. */
@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 16)
    private String color;

    @Column(name = "parent_tag_id")
    private UUID parentTagId;

    public static Tag create(UUID workspaceId, String name, String color, UUID parentTagId) {
        Tag tag = new Tag();
        tag.workspaceId = workspaceId;
        tag.name = name;
        tag.color = color;
        tag.parentTagId = parentTagId;
        return tag;
    }

    public void update(String name, String color, UUID parentTagId) {
        if (name != null) {
            this.name = name;
        }
        if (color != null) {
            this.color = color;
        }
        this.parentTagId = parentTagId;
    }
}
