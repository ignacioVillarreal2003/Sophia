-- =====================================================================
-- V8: File storage metadata
--   * file_versions: immutable content versions of file artifacts
--   * upload_sessions: server-side tracking of multipart uploads
-- =====================================================================

CREATE TABLE file_versions (
    id              UUID PRIMARY KEY,
    artifact_id     UUID          NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id    UUID          NOT NULL,
    seq             BIGINT        NOT NULL,
    storage_key     VARCHAR(1000) NOT NULL,
    mime_type       VARCHAR(255),
    size_bytes      BIGINT,
    checksum_sha256 VARCHAR(64),
    uploaded_by     UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    lock_version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_file_versions_artifact_seq UNIQUE (artifact_id, seq)
);

CREATE INDEX idx_file_versions_storage_key ON file_versions (storage_key);

CREATE TABLE upload_sessions (
    id                       UUID PRIMARY KEY,
    workspace_id             UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    parent_id                UUID,
    initiated_by             UUID          NOT NULL,
    storage_key              VARCHAR(1000) NOT NULL,
    s3_upload_id             VARCHAR(500)  NOT NULL,
    filename                 VARCHAR(500)  NOT NULL,
    declared_mime_type       VARCHAR(255),
    declared_size_bytes      BIGINT,
    declared_checksum_sha256 VARCHAR(64),
    status                   VARCHAR(16)   NOT NULL,
    expires_at               TIMESTAMPTZ   NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    lock_version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_upload_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABORTED'))
);

-- The expiry sweep scans only active sessions ordered by deadline.
CREATE INDEX idx_upload_sessions_expiry
    ON upload_sessions (expires_at)
    WHERE status = 'ACTIVE';
