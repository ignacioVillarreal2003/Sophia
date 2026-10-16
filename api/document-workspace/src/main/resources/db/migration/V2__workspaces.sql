-- =====================================================================
-- V2: Workspace aggregate
--   * workspaces (tenant root, soft-deletable, JSONB settings)
--   * workspace_members (RBAC role + MAC clearance per user)
--   * workspace_invitations (hashed token, full lifecycle)
-- =====================================================================

CREATE TABLE workspaces (
    id           UUID PRIMARY KEY,
    name         VARCHAR(120)  NOT NULL,
    description  VARCHAR(2000),
    logo_url     VARCHAR(1000),
    color        VARCHAR(16),
    domain       VARCHAR(255),
    language     VARCHAR(10)   NOT NULL,
    owner_id     UUID          NOT NULL,
    status       VARCHAR(24)   NOT NULL,
    settings     JSONB         NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version BIGINT        NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    CONSTRAINT chk_workspaces_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

-- Domain uniqueness applies only to live workspaces.
CREATE UNIQUE INDEX uq_workspaces_domain
    ON workspaces (LOWER(domain))
    WHERE domain IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_workspaces_owner ON workspaces (owner_id) WHERE deleted_at IS NULL;

CREATE TABLE workspace_members (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL,
    email        VARCHAR(320) NOT NULL,
    display_name VARCHAR(255),
    role         VARCHAR(24)  NOT NULL,
    clearance    VARCHAR(24)  NOT NULL,
    status       VARCHAR(24)  NOT NULL,
    invited_by   UUID,
    joined_at    TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    lock_version BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspace_members_user UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_members_role CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'COMMENTER', 'VIEWER')),
    CONSTRAINT chk_members_clearance CHECK (clearance IN
        ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET')),
    CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_workspace_members_user ON workspace_members (user_id);
CREATE INDEX idx_workspace_members_email ON workspace_members (workspace_id, LOWER(email));

CREATE TABLE workspace_invitations (
    id           UUID PRIMARY KEY,
    workspace_id UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        VARCHAR(320)  NOT NULL,
    role         VARCHAR(24)   NOT NULL,
    clearance    VARCHAR(24)   NOT NULL,
    token_hash   VARCHAR(64)   NOT NULL,
    status       VARCHAR(24)   NOT NULL,
    invited_by   UUID          NOT NULL,
    message      VARCHAR(1000),
    expires_at   TIMESTAMPTZ   NOT NULL,
    accepted_by  UUID,
    responded_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_invitations_role CHECK (role IN ('ADMIN', 'EDITOR', 'COMMENTER', 'VIEWER')),
    CONSTRAINT chk_invitations_clearance CHECK (clearance IN
        ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET')),
    CONSTRAINT chk_invitations_status CHECK (status IN
        ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_invitations_workspace ON workspace_invitations (workspace_id, status);
CREATE INDEX idx_invitations_email ON workspace_invitations (LOWER(email), status);

-- The expiry job scans only pending invitations ordered by deadline.
CREATE INDEX idx_invitations_expiry
    ON workspace_invitations (expires_at)
    WHERE status = 'PENDING';

-- At most one pending invitation per (workspace, email).
CREATE UNIQUE INDEX uq_invitations_pending_email
    ON workspace_invitations (workspace_id, LOWER(email))
    WHERE status = 'PENDING';
