CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(80)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    icon        VARCHAR(40),
    image_url   VARCHAR(500),
    sort_order  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_categories_slug UNIQUE (slug),
    CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE topics (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id           UUID          NOT NULL REFERENCES categories (id),
    slug                  VARCHAR(80)   NOT NULL,
    name                  VARCHAR(120)  NOT NULL,
    description           TEXT,
    icon                  VARCHAR(40),
    image_url             VARCHAR(500),
    keywords              JSONB         NOT NULL DEFAULT '[]'::jsonb,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    search_query          VARCHAR(500),
    minimum_relevance     NUMERIC(3, 2) NOT NULL DEFAULT 0.55,
    maximum_items_per_run INT           NOT NULL DEFAULT 25,
    arxiv_categories      VARCHAR(200),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_topics_slug UNIQUE (slug),
    CONSTRAINT chk_topics_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_topics_minimum_relevance CHECK (minimum_relevance BETWEEN 0 AND 1)
);
CREATE INDEX ix_topics_category ON topics (category_id);

-- Tipos: RSS (feed fijo), RSS_SEARCH (plantilla con {query}), ARXIV, HACKERNEWS, NEWSAPI.
CREATE TABLE sources (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120)  NOT NULL,
    type          VARCHAR(30)   NOT NULL,
    url           VARCHAR(1000) NOT NULL,
    configuration JSONB         NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_sources_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_sources_type CHECK (type IN ('RSS', 'RSS_SEARCH', 'ARXIV', 'HACKERNEWS', 'NEWSAPI'))
);

CREATE TABLE topic_sources (
    topic_id  UUID NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    PRIMARY KEY (topic_id, source_id)
);
CREATE INDEX ix_topic_sources_source ON topic_sources (source_id);
