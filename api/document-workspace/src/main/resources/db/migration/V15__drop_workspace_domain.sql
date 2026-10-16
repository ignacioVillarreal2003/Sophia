-- The workspace "domain" (slug) was never used by the product; drop it together with its unique index.
DROP INDEX IF EXISTS uq_workspaces_domain;
ALTER TABLE workspaces DROP COLUMN IF EXISTS domain;
