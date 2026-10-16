-- =====================================================================
-- V4: Artifact hierarchy, favorites and tags
--   * artifacts: single-table inheritance (kind discriminator), materialized
--     path for subtree operations, MAC security label, JSONB metadata
--   * favorites: per-user favorites/pins
--   * tags + tag_artifacts: hierarchical workspace tags
-- =====================================================================

CREATE TABLE artifacts (
    id                UUID PRIMARY KEY,
    kind              VARCHAR(24)  NOT NULL,
    artifact_type     VARCHAR(32)  NOT NULL,
    workspace_id      UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    parent_id         UUID,
    path              VARCHAR(4000) NOT NULL DEFAULT '',
    name              VARCHAR(255) NOT NULL,
    owner_id          UUID         NOT NULL,
    security_label    VARCHAR(24)  NOT NULL,
    metadata          JSONB,
    position          BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    lock_version      BIGINT       NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,

    -- DOCUMENT
    icon              VARCHAR(64),
    cover_image_url   VARCHAR(1000),
    content_version   BIGINT,

    -- FILE
    mime_type         VARCHAR(255),
    size_bytes        BIGINT,
    storage_key       VARCHAR(1000),
    checksum_sha256   VARCHAR(64),
    original_filename VARCHAR(500),

    -- LINK / EMBED
    url               VARCHAR(2000),
    provider          VARCHAR(100),

    -- CODE_SNIPPET
    language          VARCHAR(64),
    content           TEXT,

    CONSTRAINT chk_artifacts_label CHECK (security_label IN
        ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET'))
);

-- Children listing (live rows only).
CREATE INDEX idx_artifacts_children
    ON artifacts (workspace_id, parent_id)
    WHERE deleted_at IS NULL;

-- Subtree scans by materialized path.
CREATE INDEX idx_artifacts_path
    ON artifacts (workspace_id, path varchar_pattern_ops);

-- Trash listing and retention purge.
CREATE INDEX idx_artifacts_trash
    ON artifacts (workspace_id, deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_artifacts_owner ON artifacts (owner_id);

CREATE TABLE favorites (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL,
    artifact_id  UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    pinned       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    lock_version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_favorites_user_artifact UNIQUE (user_id, artifact_id)
);

CREATE INDEX idx_favorites_user_workspace ON favorites (user_id, workspace_id);

CREATE TABLE tags (
    id            UUID PRIMARY KEY,
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          VARCHAR(80) NOT NULL,
    color         VARCHAR(16),
    parent_tag_id UUID REFERENCES tags (id),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    lock_version  BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_tags_workspace_name ON tags (workspace_id, LOWER(name));

CREATE TABLE tag_artifacts (
    id           UUID PRIMARY KEY,
    tag_id       UUID        NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    artifact_id  UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    assigned_by  UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    lock_version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_tag_artifacts UNIQUE (tag_id, artifact_id)
);

CREATE INDEX idx_tag_artifacts_artifact ON tag_artifacts (artifact_id);
