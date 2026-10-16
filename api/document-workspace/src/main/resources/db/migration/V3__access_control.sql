-- =====================================================================
-- V3: Resource-level access control
--   * acl_entries: discretionary grants (user- or role-scoped) per resource.
--     resource_id references the artifact hierarchy polymorphically, so it
--     is indexed but intentionally has no foreign key; artifact deletion
--     cleans its entries in the application layer.
-- =====================================================================

CREATE TABLE acl_entries (
    id              UUID PRIMARY KEY,
    workspace_id    UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    resource_id     UUID        NOT NULL,
    subject_type    VARCHAR(16) NOT NULL,
    subject_user_id UUID,
    subject_role    VARCHAR(24),
    modes           JSONB       NOT NULL,
    granted_by      UUID        NOT NULL,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    lock_version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_acl_subject_type CHECK (subject_type IN ('USER', 'ROLE')),
    CONSTRAINT chk_acl_subject_role CHECK (subject_role IS NULL OR subject_role IN
        ('OWNER', 'ADMIN', 'EDITOR', 'COMMENTER', 'VIEWER')),
    -- Exactly one subject: USER entries carry a user id, ROLE entries a role.
    CONSTRAINT chk_acl_subject CHECK (
        (subject_type = 'USER' AND subject_user_id IS NOT NULL AND subject_role IS NULL) OR
        (subject_type = 'ROLE' AND subject_role IS NOT NULL AND subject_user_id IS NULL)
    )
);

CREATE INDEX idx_acl_entries_resource ON acl_entries (resource_id);
CREATE INDEX idx_acl_entries_workspace_user ON acl_entries (workspace_id, subject_user_id)
    WHERE subject_user_id IS NOT NULL;

-- One entry per (resource, user) and per (resource, role): grants are merged
-- by updating the entry's modes, never duplicated.
CREATE UNIQUE INDEX uq_acl_entries_user
    ON acl_entries (resource_id, subject_user_id)
    WHERE subject_type = 'USER';

CREATE UNIQUE INDEX uq_acl_entries_role
    ON acl_entries (resource_id, subject_role)
    WHERE subject_type = 'ROLE';
