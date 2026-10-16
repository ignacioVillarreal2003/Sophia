package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Assignment of a tag to an artifact. */
@Entity
@Table(name = "tag_artifacts", uniqueConstraints =
        @UniqueConstraint(name = "uq_tag_artifacts", columnNames = {"tag_id", "artifact_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagArtifact extends BaseEntity {

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    public static TagArtifact of(UUID tagId, UUID artifactId, UUID assignedBy) {
        TagArtifact assignment = new TagArtifact();
        assignment.tagId = tagId;
        assignment.artifactId = artifactId;
        assignment.assignedBy = assignedBy;
        return assignment;
    }
}
