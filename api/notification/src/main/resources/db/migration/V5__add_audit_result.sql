ALTER TABLE notification_audit_log
    ADD COLUMN result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';

ALTER TABLE notification_audit_log
    ALTER COLUMN result DROP DEFAULT;

CREATE INDEX ix_notification_audit_log_result ON notification_audit_log (result);
