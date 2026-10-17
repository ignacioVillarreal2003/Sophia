-- Per-member preferences: pinned workspaces and last visit.
ALTER TABLE workspace_members ADD COLUMN pinned_at TIMESTAMPTZ;
ALTER TABLE workspace_members ADD COLUMN last_accessed_at TIMESTAMPTZ;
CREATE INDEX idx_workspace_members_pinned ON workspace_members (user_id) WHERE pinned_at IS NOT NULL;

-- Workspace-level activity (any change to its content or members) and the trash-expiry warning.
ALTER TABLE workspaces ADD COLUMN last_activity_at TIMESTAMPTZ;
ALTER TABLE workspaces ADD COLUMN purge_warning_sent_at TIMESTAMPTZ;
UPDATE workspaces w
SET last_activity_at = GREATEST(w.updated_at,
        COALESCE((SELECT MAX(a.updated_at) FROM artifacts a WHERE a.workspace_id = w.id), w.updated_at));
ALTER TABLE workspaces ALTER COLUMN last_activity_at SET NOT NULL;
CREATE INDEX idx_workspaces_last_activity ON workspaces (last_activity_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_workspaces_deleted ON workspaces (deleted_at) WHERE deleted_at IS NOT NULL;

-- 0 = no alert sent, 80 = "almost full" sent, 100 = "full" sent. Reset when usage drops again.
ALTER TABLE workspace_limits ADD COLUMN quota_alert_level INT NOT NULL DEFAULT 0;

-- Personal labels: each user organizes their own list, nobody else sees them.
CREATE TABLE workspace_labels (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL,
    name         VARCHAR(40)  NOT NULL,
    color        VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    lock_version BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_workspace_labels_user_name ON workspace_labels (user_id, LOWER(name));

CREATE TABLE workspace_label_links (
    id           UUID PRIMARY KEY,
    label_id     UUID        NOT NULL REFERENCES workspace_labels (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    lock_version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspace_label_links UNIQUE (label_id, workspace_id)
);
CREATE INDEX idx_workspace_label_links_workspace ON workspace_label_links (workspace_id);

-- People who are not members ask the workspace admins for access.
CREATE TABLE access_requests (
    id           UUID PRIMARY KEY,
    workspace_id UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID          NOT NULL,
    email        VARCHAR(320)  NOT NULL,
    message      VARCHAR(500),
    status       VARCHAR(16)   NOT NULL,
    decided_by   UUID,
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_access_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED'))
);
CREATE UNIQUE INDEX uq_access_requests_pending ON access_requests (workspace_id, user_id) WHERE status = 'PENDING';
CREATE INDEX idx_access_requests_workspace ON access_requests (workspace_id, status);

-- The owner asks the platform administrators for more storage.
CREATE TABLE storage_requests (
    id           UUID PRIMARY KEY,
    workspace_id UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    requested_by UUID          NOT NULL,
    requested_mb BIGINT        NOT NULL,
    message      VARCHAR(500),
    status       VARCHAR(16)   NOT NULL,
    handled_by   UUID,
    handled_at   TIMESTAMPTZ,
    granted_mb   BIGINT,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_storage_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED')),
    CONSTRAINT chk_storage_requests_mb CHECK (requested_mb > 0)
);
CREATE UNIQUE INDEX uq_storage_requests_pending ON storage_requests (workspace_id) WHERE status = 'PENDING';
