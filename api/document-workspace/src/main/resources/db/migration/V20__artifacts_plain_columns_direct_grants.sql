-- Artifacts keep only real columns: no JSON metadata, no security labels, no manual position,
-- no icon/cover. The hierarchy lives in the materialized path (the parent is its last id).

ALTER TABLE artifacts ADD COLUMN description VARCHAR(2000);
ALTER TABLE artifacts ADD COLUMN color VARCHAR(16);
ALTER TABLE artifacts ADD COLUMN preview_text TEXT;
ALTER TABLE artifacts ADD COLUMN thumbnail_storage_key VARCHAR(1000);
ALTER TABLE artifacts ADD COLUMN preview_storage_key VARCHAR(1000);

UPDATE artifacts SET
    description = NULLIF(metadata ->> 'description', ''),
    color = CASE WHEN kind = 'FOLDER' THEN NULLIF(metadata ->> 'color', '') END,
    preview_text = metadata ->> 'previewText',
    thumbnail_storage_key = metadata ->> 'thumbnailStorageKey',
    preview_storage_key = metadata ->> 'previewStorageKey'
WHERE metadata IS NOT NULL;

-- Templates: the description joins the common column and the snapshot becomes plain text.
UPDATE artifacts SET description = template_description WHERE kind = 'TEMPLATE' AND template_description IS NOT NULL;
ALTER TABLE artifacts ALTER COLUMN template_content TYPE TEXT USING (template_content ->> 'content');
ALTER TABLE artifacts DROP COLUMN template_description;

DROP INDEX IF EXISTS idx_artifacts_children;
ALTER TABLE artifacts DROP CONSTRAINT IF EXISTS chk_artifacts_label;
ALTER TABLE artifacts DROP COLUMN parent_id;
ALTER TABLE artifacts DROP COLUMN position;
ALTER TABLE artifacts DROP COLUMN security_label;
ALTER TABLE artifacts DROP COLUMN metadata;
ALTER TABLE artifacts DROP COLUMN icon;
ALTER TABLE artifacts DROP COLUMN cover_image_url;

-- Upload sessions no longer carry a label either.
ALTER TABLE upload_sessions DROP COLUMN IF EXISTS security_label;

-- Access comes from two places only: the member role, and grants to a specific member on a folder or file.
DELETE FROM acl_entries WHERE subject_type <> 'USER';
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_acl_subject;
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_acl_subject_role;
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_acl_subject_type;
DROP INDEX IF EXISTS uq_acl_entries_role;
DROP INDEX IF EXISTS uq_acl_entries_user;
ALTER TABLE acl_entries DROP COLUMN subject_type;
ALTER TABLE acl_entries DROP COLUMN subject_role;
ALTER TABLE acl_entries ALTER COLUMN subject_user_id SET NOT NULL;
CREATE UNIQUE INDEX uq_acl_entries_user ON acl_entries (resource_id, subject_user_id);
