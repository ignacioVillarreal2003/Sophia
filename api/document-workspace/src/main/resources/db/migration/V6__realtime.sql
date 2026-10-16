-- =====================================================================
-- V6: Realtime collaboration
--   * yjs_updates: append-only binary CRDT updates per document; rows are
--     periodically compacted into a single merged state update.
-- =====================================================================

CREATE TABLE yjs_updates (
    id           BIGSERIAL PRIMARY KEY,
    document_id  UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL,
    payload      BYTEA       NOT NULL,
    actor_id     UUID        NOT NULL,
    snapshot     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_yjs_updates_document ON yjs_updates (document_id, id);
