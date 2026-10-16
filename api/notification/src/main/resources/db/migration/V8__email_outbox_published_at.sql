ALTER TABLE email_outbox
    ADD COLUMN published_at   TIMESTAMPTZ,
    ADD COLUMN last_error     VARCHAR(2000),
    ADD COLUMN correlation_id VARCHAR(64);

UPDATE email_outbox
SET published_at = COALESCE(sent_at, failed_at)
WHERE sent_at IS NOT NULL
   OR failed_at IS NOT NULL;

DROP INDEX ix_email_outbox_pending;
DROP INDEX ix_email_outbox_sent;
DROP INDEX ix_email_outbox_failed;

ALTER TABLE email_outbox
    DROP COLUMN sent_at,
    DROP COLUMN failed_at;

CREATE INDEX ix_email_outbox_pending ON email_outbox (next_attempt_at) WHERE published_at IS NULL;
CREATE INDEX ix_email_outbox_published ON email_outbox (published_at) WHERE published_at IS NOT NULL;
