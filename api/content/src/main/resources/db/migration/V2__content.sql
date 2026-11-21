CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE pipeline_runs (
    id              UUID PRIMARY KEY,
    trigger_type    VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    started_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    topics_planned  INT           NOT NULL DEFAULT 0,
    items_collected INT           NOT NULL DEFAULT 0,
    items_processed INT           NOT NULL DEFAULT 0,
    items_published INT           NOT NULL DEFAULT 0,
    items_duplicate INT           NOT NULL DEFAULT 0,
    items_rejected  INT           NOT NULL DEFAULT 0,
    items_failed    INT           NOT NULL DEFAULT 0,
    notes           VARCHAR(2000),
    CONSTRAINT chk_pipeline_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT chk_pipeline_runs_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL'))
);
CREATE INDEX ix_pipeline_runs_started ON pipeline_runs (started_at DESC);

CREATE TABLE pipeline_errors (
    id         UUID PRIMARY KEY,
    run_id     UUID REFERENCES pipeline_runs (id) ON DELETE SET NULL,
    stage      VARCHAR(30)   NOT NULL,
    source     VARCHAR(300),
    topic_id   UUID,
    message    VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_pipeline_errors_created ON pipeline_errors (created_at DESC);
CREATE INDEX ix_pipeline_errors_run ON pipeline_errors (run_id);

CREATE TABLE contents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_url            VARCHAR(2000)    NOT NULL,
    url_hash                VARCHAR(64)      NOT NULL,
    content_hash            VARCHAR(64)      NOT NULL,
    simhash                 BIGINT           NOT NULL,
    embedding               vector(384),
    source_id               UUID,
    source_name             VARCHAR(200),
    original_title          VARCHAR(1000)    NOT NULL,
    title                   VARCHAR(500)     NOT NULL,
    summary                 VARCHAR(5000)    NOT NULL,
    body_excerpt            VARCHAR(2000),
    image_url               VARCHAR(2000),
    author                  VARCHAR(300),
    language                VARCHAR(8),
    keywords                JSONB            NOT NULL DEFAULT '[]'::jsonb,
    entities                JSONB            NOT NULL DEFAULT '[]'::jsonb,
    publication_date        TIMESTAMPTZ,
    collected_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    quality_score           DOUBLE PRECISION,
    status                  VARCHAR(20)      NOT NULL,
    relation                VARCHAR(20)      NOT NULL DEFAULT 'NEW',
    related_content_id      UUID REFERENCES contents (id) ON DELETE SET NULL,
    similarity              DOUBLE PRECISION,
    rejection_reason        VARCHAR(1000),
    ai_mode                 VARCHAR(30),
    edited_by_administrator BOOLEAN          NOT NULL DEFAULT FALSE,
    run_id                  UUID,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    version                 BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT ux_contents_url_hash UNIQUE (url_hash),
    CONSTRAINT chk_contents_status CHECK (status IN ('PUBLISHED', 'DUPLICATE', 'REJECTED', 'HIDDEN')),
    CONSTRAINT chk_contents_relation CHECK (relation IN ('NEW', 'UPDATE', 'DUPLICATE'))
);
CREATE INDEX ix_contents_content_hash ON contents (content_hash);
CREATE INDEX ix_contents_status_collected ON contents (status, collected_at DESC);
CREATE INDEX ix_contents_embedding ON contents USING hnsw (embedding vector_cosine_ops);

CREATE TABLE content_topics (
    content_id UUID             NOT NULL REFERENCES contents (id) ON DELETE CASCADE,
    topic_id   UUID             NOT NULL,
    relevance  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (content_id, topic_id)
);
CREATE INDEX ix_content_topics_topic ON content_topics (topic_id, created_at DESC);
