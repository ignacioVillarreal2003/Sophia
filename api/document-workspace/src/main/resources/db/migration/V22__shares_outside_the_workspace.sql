-- A folder or document can be shared with someone who is not a member of the workspace. Those entries show up in
-- that person's "Shared with me", so they carry enough to render it without asking another service.
ALTER TABLE acl_entries ADD COLUMN subject_email VARCHAR(255);
ALTER TABLE acl_entries ADD COLUMN subject_name VARCHAR(255);
ALTER TABLE acl_entries ADD COLUMN granted_by_email VARCHAR(255);

CREATE INDEX idx_acl_entries_subject ON acl_entries (subject_user_id, created_at DESC);
