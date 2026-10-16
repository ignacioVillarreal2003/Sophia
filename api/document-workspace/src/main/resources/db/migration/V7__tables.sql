-- =====================================================================
-- V7: Table/database artifacts
--   * table_columns: typed schema per table artifact (config JSONB carries
--     options, relation targets, rollup aggregation, formula expression)
--   * table_rows: row data as JSONB keyed by column id; computed columns
--     (formula/lookup/rollup) are derived on read, never stored
-- =====================================================================

CREATE TABLE table_columns (
    id           UUID PRIMARY KEY,
    table_id     UUID         NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id UUID         NOT NULL,
    name         VARCHAR(120) NOT NULL,
    type         VARCHAR(24)  NOT NULL,
    config       JSONB,
    sort_key     VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    lock_version BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_table_columns_type CHECK (type IN (
        'TEXT', 'NUMBER', 'CHECKBOX', 'SELECT', 'MULTI_SELECT', 'DATE', 'USER',
        'TAGS', 'STATUS', 'URL', 'EMAIL', 'ATTACHMENT', 'RELATION', 'LOOKUP',
        'ROLLUP', 'FORMULA'))
);

CREATE INDEX idx_table_columns_table ON table_columns (table_id, sort_key);
CREATE UNIQUE INDEX uq_table_columns_name ON table_columns (table_id, LOWER(name));

CREATE TABLE table_rows (
    id           UUID PRIMARY KEY,
    table_id     UUID        NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL,
    values       JSONB       NOT NULL,
    sort_key     VARCHAR(64) NOT NULL,
    created_by   UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    lock_version BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_table_rows_table ON table_rows (table_id, sort_key);

-- GIN index accelerates JSONB filters (jsonb_exists, containment).
CREATE INDEX idx_table_rows_values ON table_rows USING GIN (values);
