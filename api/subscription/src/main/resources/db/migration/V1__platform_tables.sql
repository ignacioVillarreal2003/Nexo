-- Tablas comunes a todos los servicios de la plataforma.

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    user_email    VARCHAR(255),
    action        VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(255),
    details       JSONB,
    result        VARCHAR(16)  NOT NULL,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(512),
    request_id    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX ix_audit_logs_user ON audit_logs (user_id);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);

CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
