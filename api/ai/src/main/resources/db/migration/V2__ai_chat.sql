-- Conversations: chat with documents, Socratic tutor and debate partner. Only the person who started one can open it.
CREATE TABLE ai_conversations (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    workspace_id UUID,
    mode         VARCHAR(16)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    topic        VARCHAR(500),
    stance       VARCHAR(8),
    language     VARCHAR(8),
    sources      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_conversations_mode CHECK (mode IN ('DOCUMENTS', 'SOCRATIC', 'DEBATE')),
    CONSTRAINT chk_ai_conversations_stance CHECK (stance IS NULL OR stance IN ('PRO', 'CON'))
);

CREATE INDEX idx_ai_conversations_user ON ai_conversations (user_id, updated_at DESC);
CREATE INDEX idx_ai_conversations_updated ON ai_conversations (updated_at);

CREATE TABLE ai_messages (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    role            VARCHAR(12) NOT NULL,
    content         TEXT        NOT NULL,
    citations       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_ai_messages_conversation ON ai_messages (conversation_id, created_at);

-- Search index over the documents used in conversations. It holds copies of text, so it is only ever read after the
-- caller proved (with their own token) that they can open the document, and it is rebuilt whenever the text changes.
CREATE TABLE ai_doc_index (
    artifact_id  UUID        PRIMARY KEY,
    workspace_id UUID        NOT NULL,
    content_hash CHAR(64)    NOT NULL,
    chunk_count  INTEGER     NOT NULL,
    indexed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_doc_chunks (
    artifact_id UUID     NOT NULL REFERENCES ai_doc_index (artifact_id) ON DELETE CASCADE,
    position    INTEGER  NOT NULL,
    content     TEXT     NOT NULL,
    tsv         TSVECTOR NOT NULL,
    PRIMARY KEY (artifact_id, position)
);

CREATE INDEX idx_ai_doc_chunks_tsv ON ai_doc_chunks USING GIN (tsv);
CREATE INDEX idx_ai_doc_index_indexed ON ai_doc_index (indexed_at);
