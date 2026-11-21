CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL,
    topic_id            UUID        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_content_limit INT         NOT NULL DEFAULT 5,
    subscribed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_subscriptions_user_topic UNIQUE (user_id, topic_id),
    CONSTRAINT chk_subscriptions_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_subscriptions_daily_limit CHECK (daily_content_limit BETWEEN 1 AND 50)
);
CREATE INDEX ix_subscriptions_topic_active ON subscriptions (topic_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_subscriptions_user ON subscriptions (user_id);

CREATE TABLE delivery_settings (
    user_id             UUID PRIMARY KEY,
    timezone            VARCHAR(64) NOT NULL,
    delivery_hour       INT         NOT NULL,
    maximum_daily_items INT         NOT NULL,
    paused              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_delivery_settings_hour CHECK (delivery_hour BETWEEN 0 AND 23),
    CONSTRAINT chk_delivery_settings_maximum CHECK (maximum_daily_items BETWEEN 1 AND 200)
);
