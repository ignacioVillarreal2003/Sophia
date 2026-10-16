-- =====================================================================
-- V5: Block-based document content and revision history
--   * blocks: one row per content block; ordering via fractional sort keys
--   * revisions: append-only operation log with periodic snapshots
-- =====================================================================

CREATE TABLE blocks (
    id              UUID PRIMARY KEY,
    document_id     UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL,
    parent_block_id UUID,
    type            VARCHAR(24) NOT NULL,
    content         JSONB       NOT NULL,
    sort_key        VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_blocks_type CHECK (type IN (
        'HEADING', 'PARAGRAPH', 'IMAGE', 'CODE', 'TABLE', 'QUOTE', 'CALLOUT',
        'DIVIDER', 'CHECKLIST_ITEM', 'TOGGLE', 'EMBED', 'EQUATION', 'HTML',
        'FILE', 'BOOKMARK', 'COLUMNS', 'COLUMN'))
);

CREATE INDEX idx_blocks_document ON blocks (document_id, sort_key);
CREATE INDEX idx_blocks_parent ON blocks (parent_block_id) WHERE parent_block_id IS NOT NULL;

CREATE TABLE revisions (
    id           UUID PRIMARY KEY,
    document_id  UUID          NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id UUID          NOT NULL,
    seq          BIGINT        NOT NULL,
    actor_id     UUID          NOT NULL,
    operations   JSONB         NOT NULL,
    snapshot     JSONB,
    version_name VARCHAR(120),
    comment      VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_revisions_document_seq UNIQUE (document_id, seq)
);

-- Checkpoint lookup: newest snapshot at or below a version.
CREATE INDEX idx_revisions_checkpoints
    ON revisions (document_id, seq DESC)
    WHERE snapshot IS NOT NULL;

CREATE INDEX idx_revisions_named
    ON revisions (document_id, seq DESC)
    WHERE version_name IS NOT NULL;
