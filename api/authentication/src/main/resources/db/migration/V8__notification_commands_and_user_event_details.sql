ALTER TABLE user_event_outbox
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name  VARCHAR(100),
    ADD COLUMN locale     VARCHAR(10),
    ADD COLUMN timezone   VARCHAR(50);

CREATE TABLE notification_command_outbox (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_key VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_command_outbox_pending ON notification_command_outbox (occurred_at) WHERE published_at IS NULL;
CREATE INDEX ix_notification_command_outbox_published ON notification_command_outbox (published_at) WHERE published_at IS NOT NULL;

DROP TABLE email_outbox;

ALTER TABLE user_preferences
    DROP COLUMN notify_email_enabled,
    DROP COLUMN notify_push_enabled;
