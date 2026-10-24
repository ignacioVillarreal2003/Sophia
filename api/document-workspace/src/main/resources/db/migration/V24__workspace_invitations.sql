-- Adding someone to a workspace is now an invitation they accept (or decline) before they become a member.
CREATE TABLE workspace_invitations (
    id               UUID PRIMARY KEY,
    workspace_id     UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    invitee_user_id  UUID          NOT NULL,
    invitee_email    VARCHAR(320)  NOT NULL,
    invitee_name     VARCHAR(255),
    role             VARCHAR(24)   NOT NULL,
    invited_by       UUID          NOT NULL,
    invited_by_email VARCHAR(320),
    status           VARCHAR(16)   NOT NULL,
    expires_at       TIMESTAMPTZ   NOT NULL,
    responded_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    lock_version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_workspace_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED')),
    CONSTRAINT chk_workspace_invitations_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
);
CREATE UNIQUE INDEX uq_workspace_invitations_pending ON workspace_invitations (workspace_id, invitee_user_id) WHERE status = 'PENDING';
CREATE INDEX idx_workspace_invitations_invitee ON workspace_invitations (invitee_user_id, status, created_at DESC);
CREATE INDEX idx_workspace_invitations_workspace ON workspace_invitations (workspace_id, status);
