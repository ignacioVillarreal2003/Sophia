CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    auth_provider         VARCHAR(20)  NOT NULL,
    last_login_at         TIMESTAMPTZ,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    deleted_by            VARCHAR(100),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'))
);

CREATE UNIQUE INDEX ux_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_active ON users (active) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_created_at ON users (created_at);

CREATE TABLE user_preferences (
    user_id                  UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    photo_url                VARCHAR(512),
    theme                    VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    locale                   VARCHAR(10)  NOT NULL DEFAULT 'en-US',
    timezone                 VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    notify_email_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_push_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_marketing_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_user_preferences_theme CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM'))
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_roles_name UNIQUE (name)
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    category    VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_permissions_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TABLE user_permissions (
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, permission_id)
);
CREATE INDEX ix_user_permissions_permission_id ON user_permissions (permission_id);

CREATE TABLE user_sessions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    device_name    VARCHAR(255),
    remember_me    BOOLEAN      NOT NULL DEFAULT FALSE,
    last_active_at TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX ix_user_sessions_expires_at ON user_sessions (expires_at);

CREATE TABLE refresh_tokens (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash             VARCHAR(64)  NOT NULL,
    user_id                UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id             UUID         NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    expires_at             TIMESTAMPTZ  NOT NULL,
    revoked_at             TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(64),
    created_by_ip          VARCHAR(45),
    user_agent             VARCHAR(512),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ,
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_session_id ON refresh_tokens (session_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE oauth_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    name             VARCHAR(255),
    access_token     TEXT,
    linked_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_oauth_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ux_oauth_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT chk_oauth_accounts_provider CHECK (provider IN ('GOOGLE'))
);

CREATE TABLE password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_history_user_created ON password_history (user_id, created_at DESC);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_password_reset_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    new_email  VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_email_verification_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'EMAIL_CHANGE'))
);
CREATE INDEX ix_email_verification_tokens_user_id ON email_verification_tokens (user_id);

CREATE TABLE login_attempts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    successful     BOOLEAN      NOT NULL,
    failure_reason VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_attempts_email_created ON login_attempts (email, created_at DESC);
CREATE INDEX ix_login_attempts_ip_created ON login_attempts (ip_address, created_at DESC);

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    user_email  VARCHAR(255),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(255),
    details     JSONB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    status      VARCHAR(20)  NOT NULL,
    request_id  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_logs_status CHECK (status IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_user_created ON audit_logs (user_id, created_at DESC);
CREATE INDEX ix_audit_logs_action_created ON audit_logs (action, created_at DESC);
CREATE INDEX ix_audit_logs_status_created ON audit_logs (status, created_at DESC);

CREATE TABLE service_clients (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    secret_hash  VARCHAR(100) NOT NULL,
    scopes       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_service_clients_name UNIQUE (name)
);
