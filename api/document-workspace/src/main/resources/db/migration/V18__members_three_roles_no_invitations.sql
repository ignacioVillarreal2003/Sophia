-- Invitations are gone: members are added directly by a workspace ADMIN.
DROP TABLE workspace_invitations;

-- Three roles only. The owner is workspaces.owner_id (and is kept as an ADMIN member row).
ALTER TABLE workspace_members DROP CONSTRAINT chk_members_role;
UPDATE workspace_members SET role = 'ADMIN' WHERE role = 'OWNER';
UPDATE workspace_members SET role = 'VIEWER' WHERE role = 'COMMENTER';
ALTER TABLE workspace_members
    ADD CONSTRAINT chk_members_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'));

ALTER TABLE acl_entries DROP CONSTRAINT chk_acl_subject_role;
UPDATE acl_entries SET subject_role = 'ADMIN' WHERE subject_role = 'OWNER';
UPDATE acl_entries SET subject_role = 'VIEWER' WHERE subject_role = 'COMMENTER';
ALTER TABLE acl_entries
    ADD CONSTRAINT chk_acl_subject_role CHECK (subject_role IS NULL OR subject_role IN ('ADMIN', 'EDITOR', 'VIEWER'));

-- Clearance is not a member attribute any more.
ALTER TABLE workspace_members DROP CONSTRAINT chk_members_clearance;
ALTER TABLE workspace_members DROP COLUMN clearance;

ALTER TABLE workspace_members ADD COLUMN updated_by UUID;

-- Every workspace has a limits row; new ones get the default quota on creation.
INSERT INTO workspace_limits (workspace_id, storage_quota_mb, created_at, updated_at)
SELECT w.id, 5120, now(), now()
FROM workspaces w
WHERE NOT EXISTS (SELECT 1 FROM workspace_limits l WHERE l.workspace_id = w.id);
