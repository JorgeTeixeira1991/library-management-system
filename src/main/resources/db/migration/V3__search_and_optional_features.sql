CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE book
ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple'::regconfig, coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple'::regconfig, coalesce(authors, '')), 'A') ||
    setweight(to_tsvector('simple'::regconfig, coalesce(category, '')), 'B') ||
    setweight(to_tsvector('simple'::regconfig, coalesce(description, '')), 'C')
) STORED;

CREATE INDEX idx_book_search_vector ON book USING GIN (search_vector);
CREATE INDEX idx_book_title_trgm ON book USING GIN (title gin_trgm_ops);
CREATE INDEX idx_book_authors_trgm ON book USING GIN (authors gin_trgm_ops);

CREATE TABLE chat_conversation (
    id BIGSERIAL PRIMARY KEY,
    conversation_key VARCHAR(100) NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES chat_conversation(id),
    sender_type VARCHAR(20) NOT NULL CHECK (sender_type IN ('USER', 'ASSISTANT', 'TOOL')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_chat_message_conversation_created
    ON chat_message(conversation_id, created_at);

CREATE TABLE metadata_enrichment_job (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id),
    source VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    raw_payload TEXT,
    error_message TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_metadata_job_book_created
    ON metadata_enrichment_job(book_id, created_at DESC);
