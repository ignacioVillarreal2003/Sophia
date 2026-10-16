-- Read-only public links: anyone holding the token can open one artifact without signing in.
-- Only the SHA-256 hash of the token is stored; the raw token is shown once, when the link is created.
CREATE TABLE public_links (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    artifact_id  UUID         NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL,
    created_by   UUID         NOT NULL,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    lock_version BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_public_links_token UNIQUE (token_hash)
);

CREATE INDEX idx_public_links_artifact ON public_links (artifact_id) WHERE revoked_at IS NULL;
