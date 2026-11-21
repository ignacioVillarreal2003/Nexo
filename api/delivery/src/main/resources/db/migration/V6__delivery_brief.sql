-- Copia del informe completo de cada noticia entregada (resumen en párrafos, puntos clave, por qué importa), en
-- español y en inglés. Lo escribe content en segundo plano; brief_ready indica si ya llegó.
ALTER TABLE deliveries ADD COLUMN detailed_summary TEXT;
ALTER TABLE deliveries ADD COLUMN detailed_summary_en TEXT;
ALTER TABLE deliveries ADD COLUMN key_points JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE deliveries ADD COLUMN key_points_en JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE deliveries ADD COLUMN why_it_matters VARCHAR(1500);
ALTER TABLE deliveries ADD COLUMN why_it_matters_en VARCHAR(1500);
ALTER TABLE deliveries ADD COLUMN reading_minutes INT;
ALTER TABLE deliveries ADD COLUMN brief_ready BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_deliveries_without_brief ON deliveries (delivered_at DESC) WHERE brief_ready = FALSE;

-- Correos de resumen pendientes: se mandan cuando los informes de sus noticias están listos (o pasado un tiempo
-- máximo de espera, con lo que haya).
CREATE TABLE email_requests (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    delivery_date DATE         NOT NULL,
    on_demand     BOOLEAN      NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    requested_at  TIMESTAMPTZ  NOT NULL,
    sent_at       TIMESTAMPTZ,
    item_count    INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_email_requests_status CHECK (status IN ('PENDING', 'SENT', 'EMPTY'))
);
CREATE INDEX ix_email_requests_pending ON email_requests (requested_at) WHERE status = 'PENDING';
CREATE INDEX ix_email_requests_user ON email_requests (user_id, delivery_date);
