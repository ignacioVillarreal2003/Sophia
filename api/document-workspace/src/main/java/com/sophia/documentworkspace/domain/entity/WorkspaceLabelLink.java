package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Puts one of the user's labels on one workspace. */
@Entity
@Table(name = "workspace_label_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceLabelLink extends BaseEntity {

    @Column(name = "label_id", nullable = false)
    private UUID labelId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    public static WorkspaceLabelLink of(UUID labelId, UUID workspaceId) {
        WorkspaceLabelLink link = new WorkspaceLabelLink();
        link.labelId = labelId;
        link.workspaceId = workspaceId;
        return link;
    }
}
