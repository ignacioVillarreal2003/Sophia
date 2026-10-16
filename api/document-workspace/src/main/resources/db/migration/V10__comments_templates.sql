-- =====================================================================
-- V10: Comments and templates
--   * comments: threaded, block-anchored, soft-deleted, JSONB reactions
--   * template columns on artifacts (SINGLE_TABLE subclass TemplateArtifact)
-- =====================================================================

CREATE TABLE comments (
    id                UUID PRIMARY KEY,
    workspace_id      UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    artifact_id       UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    block_id          UUID,
    parent_comment_id UUID REFERENCES comments (id) ON DELETE CASCADE,
    author_id         UUID        NOT NULL,
    body              TEXT,
    mentions          JSONB,
    reactions         JSONB,
    edited            BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at       TIMESTAMPTZ,
    resolved_by       UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    lock_version      BIGINT      NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID
);

CREATE INDEX idx_comments_artifact ON comments (artifact_id, created_at);
CREATE INDEX idx_comments_parent ON comments (parent_comment_id)
    WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_comments_block ON comments (block_id) WHERE block_id IS NOT NULL;

-- Template subclass columns on the single artifacts table.
ALTER TABLE artifacts
    ADD COLUMN template_target_type VARCHAR(32),
    ADD COLUMN template_description VARCHAR(2000),
    ADD COLUMN template_content     JSONB,
    ADD COLUMN template_published   BOOLEAN,
    ADD COLUMN template_version     BIGINT;

CREATE INDEX idx_artifacts_templates
    ON artifacts (workspace_id)
    WHERE kind = 'TEMPLATE' AND deleted_at IS NULL;
