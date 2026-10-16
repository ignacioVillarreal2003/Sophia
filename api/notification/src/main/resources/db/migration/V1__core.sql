CREATE TABLE user_contacts
(
    user_id    UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    locale     VARCHAR(10),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification_categories
(
    code         VARCHAR(80)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    is_mandatory BOOLEAN      NOT NULL DEFAULT false,
    is_system    BOOLEAN      NOT NULL DEFAULT false,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE notification_channels
(
    code       VARCHAR(20)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notifications
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    recipient_id UUID         NOT NULL,
    created_by   UUID,
    category     VARCHAR(80)  NOT NULL REFERENCES notification_categories (code),
    type         VARCHAR(40)  NOT NULL,
    severity     VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    priority     SMALLINT     NOT NULL DEFAULT 0,
    group_key    VARCHAR(200),
    target_type  VARCHAR(40),
    target_id    UUID,
    title        VARCHAR(255) NOT NULL,
    body         VARCHAR(2000),
    data         JSONB,
    scheduled_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    read_at      TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_notifications_severity CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR'))
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_notifications_unread ON notifications (recipient_id)
    WHERE read_at IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_notifications_created_at ON notifications (created_at);
CREATE INDEX ix_notifications_category ON notifications (recipient_id, category)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_notifications_group ON notifications (recipient_id, group_key)
    WHERE group_key IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_notifications_expires_at ON notifications (expires_at)
    WHERE expires_at IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_notifications_scheduled_at ON notifications (scheduled_at)
    WHERE scheduled_at IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE notification_channel_preferences
(
    user_id    UUID        NOT NULL,
    channel    VARCHAR(20) NOT NULL REFERENCES notification_channels (code),
    enabled    BOOLEAN     NOT NULL DEFAULT true,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, channel)
);

CREATE TABLE notification_category_channel_preferences
(
    user_id    UUID        NOT NULL,
    category   VARCHAR(80) NOT NULL REFERENCES notification_categories (code),
    channel    VARCHAR(20) NOT NULL REFERENCES notification_channels (code),
    enabled    BOOLEAN     NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, category, channel)
);
