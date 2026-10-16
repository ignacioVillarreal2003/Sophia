CREATE TABLE shedlock
(
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE notification_dispatches
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID REFERENCES notifications (id) ON DELETE CASCADE,
    message_id      UUID         NOT NULL,
    correlation_id  VARCHAR(64),
    recipient_id    UUID,
    channel         VARCHAR(20)  NOT NULL REFERENCES notification_channels (code),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_dispatches_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DELIVERED'))
);
CREATE INDEX ix_notification_dispatches_notification ON notification_dispatches (notification_id)
    WHERE notification_id IS NOT NULL;
CREATE INDEX ix_notification_dispatches_recipient ON notification_dispatches (recipient_id, created_at DESC);
CREATE INDEX ix_notification_dispatches_correlation ON notification_dispatches (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE email_outbox
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatch_id     UUID REFERENCES notification_dispatches (id),
    category        VARCHAR(80)  REFERENCES notification_categories (code),
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body            TEXT         NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_outbox_pending ON email_outbox (next_attempt_at) WHERE sent_at IS NULL AND failed_at IS NULL;
CREATE INDEX ix_email_outbox_sent ON email_outbox (sent_at) WHERE sent_at IS NOT NULL;
CREATE INDEX ix_email_outbox_failed ON email_outbox (failed_at) WHERE failed_at IS NOT NULL;

CREATE TABLE processed_messages
(
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_processed_messages_processed_at ON processed_messages (processed_at);

CREATE TABLE notification_audit_log
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id       UUID         NOT NULL,
    action         VARCHAR(60)  NOT NULL,
    subject_id     UUID,
    detail         VARCHAR(500),
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_audit_log_actor ON notification_audit_log (actor_id, occurred_at DESC);
CREATE INDEX ix_notification_audit_log_subject ON notification_audit_log (subject_id, occurred_at DESC)
    WHERE subject_id IS NOT NULL;
CREATE INDEX ix_notification_audit_log_occurred_at ON notification_audit_log (occurred_at);
