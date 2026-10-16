ALTER TABLE notification_audit_log RENAME TO audit_logs;
ALTER TABLE audit_logs RENAME COLUMN actor_id TO user_id;
ALTER TABLE audit_logs RENAME COLUMN correlation_id TO request_id;
ALTER TABLE audit_logs RENAME COLUMN occurred_at TO created_at;
ALTER TABLE audit_logs ADD COLUMN user_email VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(512);
ALTER TABLE audit_logs ALTER COLUMN action TYPE VARCHAR(128);

UPDATE audit_logs
SET resource_type = 'User', resource_id = subject_id::text
WHERE subject_id IS NOT NULL AND resource_id IS NULL;

ALTER TABLE audit_logs DROP COLUMN subject_id;
ALTER TABLE audit_logs ADD CONSTRAINT chk_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'));

ALTER INDEX notification_audit_log_pkey RENAME TO audit_logs_pkey;
ALTER INDEX ix_notification_audit_log_actor RENAME TO ix_audit_logs_user;
ALTER INDEX ix_notification_audit_log_occurred_at RENAME TO ix_audit_logs_created_at;
ALTER INDEX ix_notification_audit_log_resource RENAME TO ix_audit_logs_resource;
ALTER INDEX ix_notification_audit_log_result RENAME TO ix_audit_logs_result;
