-- =====================================================================
-- V9: Notifications and audit trail
--   (search lives in OpenSearch; no relational schema required)
-- =====================================================================

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    workspace_id UUID,
    recipient_id UUID          NOT NULL,
    type         VARCHAR(24)   NOT NULL,
    title        VARCHAR(255)  NOT NULL,
    body         VARCHAR(2000),
    artifact_id  UUID,
    actor_id     UUID,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'COMMENT', 'MENTION', 'INVITATION', 'MEMBERSHIP', 'CHANGE', 'SHARED', 'REMINDER'))
);

CREATE INDEX idx_notifications_recipient
    ON notifications (recipient_id, created_at DESC);

CREATE INDEX idx_notifications_unread
    ON notifications (recipient_id)
    WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY,
    workspace_id  UUID,
    user_id       UUID,
    action        VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id   UUID,
    result        VARCHAR(16)  NOT NULL,
    ip_address    VARCHAR(45),
    request_id    VARCHAR(64),
    details       JSONB,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_logs_workspace ON audit_logs (workspace_id, created_at DESC);
CREATE INDEX idx_audit_logs_user ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs (resource_id) WHERE resource_id IS NOT NULL;
