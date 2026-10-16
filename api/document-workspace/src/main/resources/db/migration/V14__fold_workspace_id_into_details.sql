UPDATE audit_logs
SET details = COALESCE(details, '{}'::jsonb) || jsonb_build_object('workspaceId', workspace_id::text)
WHERE workspace_id IS NOT NULL;

DROP INDEX idx_audit_logs_workspace;
ALTER TABLE audit_logs DROP COLUMN workspace_id;

CREATE INDEX idx_audit_logs_workspace ON audit_logs ((details ->> 'workspaceId'));
