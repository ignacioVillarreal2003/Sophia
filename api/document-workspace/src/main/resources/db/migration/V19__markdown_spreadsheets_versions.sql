-- Documents become plain text: Markdown notes, checklists and spreadsheets (JSON).
-- Block storage, CRDT/revisions of blocks and the typed-table stack are removed.

-- Types that no longer exist are removed together with everything attached to them.
DELETE FROM comments WHERE artifact_id IN (SELECT id FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE'));
DELETE FROM favorites WHERE artifact_id IN (SELECT id FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE'));
DELETE FROM tag_artifacts WHERE artifact_id IN (SELECT id FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE'));
DELETE FROM acl_entries WHERE resource_id IN (SELECT id FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE'));
DELETE FROM public_links WHERE artifact_id IN (SELECT id FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE'));
DELETE FROM artifacts WHERE artifact_type IN ('LINK', 'EMBED', 'CODE_SNIPPET', 'TABLE', 'DATABASE');

-- Rich text documents are plain Markdown documents now (their content is converted
-- from blocks by the application on first start, before the block tables are dropped).
UPDATE artifacts SET artifact_type = 'MARKDOWN_DOCUMENT' WHERE artifact_type = 'RICH_TEXT';
UPDATE artifacts SET kind = 'DOCUMENT' WHERE artifact_type = 'SPREADSHEET';

-- Template targets that no longer exist.
UPDATE artifacts SET template_target_type = 'MARKDOWN_DOCUMENT' WHERE template_target_type = 'RICH_TEXT';
DELETE FROM artifacts WHERE artifact_type = 'TEMPLATE' AND template_target_type IN ('TABLE', 'DATABASE', 'LINK', 'EMBED', 'CODE_SNIPPET');

ALTER TABLE artifacts DROP COLUMN IF EXISTS url;
ALTER TABLE artifacts DROP COLUMN IF EXISTS provider;
ALTER TABLE artifacts DROP COLUMN IF EXISTS language;

CREATE TABLE document_versions (
    id           UUID PRIMARY KEY,
    artifact_id  UUID         NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    version      BIGINT       NOT NULL,
    content      TEXT,
    name         VARCHAR(120),
    created_by   UUID         NOT NULL,
    saved_at     TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    lock_version BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_versions_seq UNIQUE (artifact_id, version)
);

CREATE INDEX idx_document_versions_artifact ON document_versions (artifact_id, version DESC);

-- Convert the block content of existing documents into Markdown text.
UPDATE artifacts a
SET content = sub.md
FROM (
    SELECT b.document_id,
           string_agg(
               CASE b.type
                   WHEN 'HEADING' THEN repeat('#', LEAST(GREATEST(COALESCE((b.content ->> 'level')::int, 1), 1), 6)) || ' ' || COALESCE(b.content ->> 'text', '')
                   WHEN 'CHECKLIST_ITEM' THEN '- [' || CASE WHEN (b.content ->> 'checked') = 'true' THEN 'x' ELSE ' ' END || '] ' || COALESCE(b.content ->> 'text', '')
                   WHEN 'QUOTE' THEN '> ' || COALESCE(b.content ->> 'text', '')
                   WHEN 'CALLOUT' THEN '> [!note] ' || COALESCE(b.content ->> 'text', '')
                   WHEN 'CODE' THEN '```' || COALESCE(b.content ->> 'language', '') || E'\n' || COALESCE(b.content ->> 'code', '') || E'\n```'
                   WHEN 'DIVIDER' THEN '---'
                   WHEN 'IMAGE' THEN '![' || COALESCE(b.content ->> 'alt', '') || '](' || COALESCE(b.content ->> 'url', '') || ')'
                   ELSE COALESCE(b.content ->> 'text', '')
               END,
               E'\n\n' ORDER BY b.sort_key) AS md
    FROM blocks b
    GROUP BY b.document_id
) sub
WHERE a.id = sub.document_id AND a.content IS NULL;

-- Seed every existing document version history with its current content.
INSERT INTO document_versions (id, artifact_id, workspace_id, version, content, created_by, saved_at, created_at, updated_at)
SELECT gen_random_uuid(), a.id, a.workspace_id, a.content_version, COALESCE(a.content, ''), a.owner_id, now(), now(), now()
FROM artifacts a
WHERE a.kind = 'DOCUMENT';

DROP TABLE IF EXISTS table_rows;
DROP TABLE IF EXISTS table_columns;
DROP TABLE IF EXISTS blocks;
DROP TABLE IF EXISTS revisions;
