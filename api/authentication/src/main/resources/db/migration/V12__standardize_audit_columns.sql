ALTER TABLE audit_logs RENAME COLUMN entity_type TO resource_type;
ALTER TABLE audit_logs RENAME COLUMN entity_id TO resource_id;
ALTER TABLE audit_logs RENAME COLUMN status TO result;

ALTER INDEX ix_audit_logs_status_created RENAME TO ix_audit_logs_result_created;
ALTER TABLE audit_logs RENAME CONSTRAINT chk_audit_logs_status TO chk_audit_logs_result;
