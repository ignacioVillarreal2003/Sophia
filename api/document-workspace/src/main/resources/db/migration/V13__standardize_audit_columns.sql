ALTER TABLE audit_logs ADD COLUMN user_email VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(512);
ALTER TABLE audit_logs ALTER COLUMN resource_id TYPE VARCHAR(255) USING resource_id::text;
