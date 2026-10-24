-- Runtime-editable limits (one row, created from the configured defaults on first use).
CREATE TABLE ai_settings (
    id                         SMALLINT    PRIMARY KEY CHECK (id = 1),
    enabled                    BOOLEAN     NOT NULL,
    daily_tokens_per_user      BIGINT      NOT NULL CHECK (daily_tokens_per_user >= 0),
    daily_tokens_per_workspace BIGINT      NOT NULL CHECK (daily_tokens_per_workspace >= 0),
    updated_by                 UUID,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per call to the model: who, for what, how much. No prompts or answers are stored.
CREATE TABLE ai_generations (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL,
    user_email         VARCHAR(255),
    workspace_id       UUID,
    feature            VARCHAR(32) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    model              VARCHAR(64),
    input_tokens       BIGINT      NOT NULL DEFAULT 0,
    output_tokens      BIGINT      NOT NULL DEFAULT 0,
    cache_read_tokens  BIGINT      NOT NULL DEFAULT 0,
    cache_write_tokens BIGINT      NOT NULL DEFAULT 0,
    duration_ms        BIGINT      NOT NULL DEFAULT 0,
    error_code         VARCHAR(40),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_generations_status CHECK (status IN ('SUCCESS', 'FAILED', 'REFUSED', 'CANCELLED'))
);

CREATE INDEX idx_ai_generations_user ON ai_generations (user_id, created_at DESC);
CREATE INDEX idx_ai_generations_workspace ON ai_generations (workspace_id, created_at DESC) WHERE workspace_id IS NOT NULL;
CREATE INDEX idx_ai_generations_created ON ai_generations (created_at);
