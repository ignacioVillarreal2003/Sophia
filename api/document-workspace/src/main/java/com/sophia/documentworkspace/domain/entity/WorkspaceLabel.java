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

/** A label a user creates to organize their own workspace list; invisible to everyone else. */
@Entity
@Table(name = "workspace_labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceLabel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 16)
    private String color;

    public static WorkspaceLabel create(UUID userId, String name, String color) {
        WorkspaceLabel label = new WorkspaceLabel();
        label.userId = userId;
        label.name = name;
        label.color = color;
        return label;
    }

    public void update(String name, String color) {
        if (name != null) {
            this.name = name;
        }
        if (color != null) {
            this.color = color;
        }
    }
}
