ALTER TABLE notification_audit_log DROP COLUMN detail;
ALTER TABLE notification_audit_log ADD COLUMN details JSONB;
ALTER TABLE notification_audit_log ADD COLUMN resource_type VARCHAR(64);
ALTER TABLE notification_audit_log ADD COLUMN resource_id VARCHAR(255);
ALTER TABLE notification_audit_log ADD COLUMN ip_address VARCHAR(45);

CREATE INDEX ix_notification_audit_log_resource ON notification_audit_log (resource_id)
    WHERE resource_id IS NOT NULL;
