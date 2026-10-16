package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One binary Yjs update of a document's CRDT state. The server treats updates
 * as opaque payloads: it relays them to collaborators and persists them so a
 * client joining later can replay the sequence and converge. Periodic
 * compaction replaces the accumulated rows with a single merged state update
 * (flagged {@code snapshot}) produced by a client.
 */
@Entity
@Table(name = "yjs_updates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YjsUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private byte[] payload;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    /** True when this row is a compacted full-state update. */
    @Column(nullable = false)
    private boolean snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static YjsUpdate of(UUID documentId, UUID workspaceId, byte[] payload, UUID actorId,
                               boolean snapshot) {
        YjsUpdate update = new YjsUpdate();
        update.documentId = documentId;
        update.workspaceId = workspaceId;
        update.payload = payload;
        update.actorId = actorId;
        update.snapshot = snapshot;
        update.createdAt = Instant.now();
        return update;
    }
}
