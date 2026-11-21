CREATE EXTENSION IF NOT EXISTS vector;

-- UserContentDelivery: historial de lo que recibió cada usuario.
CREATE TABLE deliveries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID          NOT NULL,
    content_id         UUID          NOT NULL,
    topic_id           UUID          NOT NULL,
    topic_name         VARCHAR(120),
    delivery_date      DATE          NOT NULL,
    delivered_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    read_at            TIMESTAMPTZ,
    favorite           BOOLEAN       NOT NULL DEFAULT FALSE,
    favorited_at       TIMESTAMPTZ,
    title              VARCHAR(500)  NOT NULL,
    summary            VARCHAR(5000) NOT NULL,
    original_url       VARCHAR(2000) NOT NULL,
    source_name        VARCHAR(200),
    image_url          VARCHAR(2000),
    publication_date   TIMESTAMPTZ,
    collected_at       TIMESTAMPTZ,
    relation           VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    related_content_id UUID,
    embedding          vector(384),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_deliveries_user_content UNIQUE (user_id, content_id),
    CONSTRAINT chk_deliveries_relation CHECK (relation IN ('NEW', 'UPDATE'))
);
CREATE INDEX ix_deliveries_user_date ON deliveries (user_id, delivery_date DESC);
CREATE INDEX ix_deliveries_user_delivered ON deliveries (user_id, delivered_at DESC);
CREATE INDEX ix_deliveries_user_favorite ON deliveries (user_id, favorited_at DESC) WHERE favorite;

CREATE TABLE delivery_batches (
    user_id       UUID        NOT NULL,
    topic_id      UUID        NOT NULL,
    delivery_date DATE        NOT NULL,
    items         INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_id, delivery_date)
);

CREATE TABLE delivery_runs (
    id                 UUID PRIMARY KEY,
    trigger_type       VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    started_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    users_processed    INT           NOT NULL DEFAULT 0,
    deliveries_created INT           NOT NULL DEFAULT 0,
    errors             INT           NOT NULL DEFAULT 0,
    notes              VARCHAR(2000)
);
CREATE INDEX ix_delivery_runs_started ON delivery_runs (started_at DESC);

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)   NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(128)  NOT NULL,
    topic          VARCHAR(255)  NOT NULL,
    correlation_id VARCHAR(64),
    payload        JSONB         NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(2000)
);
CREATE INDEX ix_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
