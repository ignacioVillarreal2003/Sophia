-- Workspace configuration becomes real columns instead of a free-form JSON document.
ALTER TABLE workspaces
    ADD COLUMN trash_retention_days   INTEGER,
    ADD COLUMN public_sharing_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN timezone               VARCHAR(50) NOT NULL DEFAULT 'UTC',
    ADD COLUMN date_format            VARCHAR(32) NOT NULL DEFAULT 'yyyy-MM-dd',
    ADD COLUMN updated_by             UUID;

UPDATE workspaces SET
    trash_retention_days   = NULLIF(settings ->> 'trashRetentionDays', '')::int,
    public_sharing_enabled = COALESCE((settings ->> 'publicSharingEnabled')::boolean, FALSE),
    timezone               = COALESCE(NULLIF(settings ->> 'timezone', ''), 'UTC'),
    date_format            = COALESCE(NULLIF(settings ->> 'dateFormat', ''), 'yyyy-MM-dd');

-- Limits are set by a platform administrator (or the system), not by the workspace itself.
CREATE TABLE workspace_limits (
    workspace_id     UUID PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    storage_quota_mb BIGINT,
    updated_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    lock_version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_workspace_limits_quota CHECK (storage_quota_mb IS NULL OR storage_quota_mb > 0)
);

INSERT INTO workspace_limits (workspace_id, storage_quota_mb, created_at, updated_at)
SELECT id, (settings ->> 'storageQuotaMb')::bigint, now(), now()
FROM workspaces
WHERE NULLIF(settings ->> 'storageQuotaMb', '') IS NOT NULL;

ALTER TABLE workspaces DROP COLUMN settings;

-- ARCHIVED becomes READ_ONLY: visible, but nothing can be changed.
ALTER TABLE workspaces DROP CONSTRAINT chk_workspaces_status;
UPDATE workspaces SET status = 'READ_ONLY' WHERE status = 'ARCHIVED';
ALTER TABLE workspaces
    ADD CONSTRAINT chk_workspaces_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'READ_ONLY'));
