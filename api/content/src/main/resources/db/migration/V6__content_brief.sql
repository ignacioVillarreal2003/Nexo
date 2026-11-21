-- Texto completo de la nota y el informe que escribe la IA cuando la noticia se elige para alguien: resumen completo
-- (en párrafos), puntos clave y por qué importa, en español y en inglés. El informe se pide desde delivery y lo genera
-- ContentBriefService en segundo plano (brief_status: PENDING → READY, o FAILED tras varios intentos).
ALTER TABLE contents ADD COLUMN body_text TEXT;
UPDATE contents SET body_text = body_excerpt WHERE body_excerpt IS NOT NULL;

ALTER TABLE contents ADD COLUMN detailed_summary TEXT;
ALTER TABLE contents ADD COLUMN detailed_summary_en TEXT;
ALTER TABLE contents ADD COLUMN key_points JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE contents ADD COLUMN key_points_en JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE contents ADD COLUMN why_it_matters VARCHAR(1500);
ALTER TABLE contents ADD COLUMN why_it_matters_en VARCHAR(1500);
ALTER TABLE contents ADD COLUMN reading_minutes INT;

ALTER TABLE contents ADD COLUMN brief_status VARCHAR(20);
ALTER TABLE contents ADD COLUMN brief_requested_at TIMESTAMPTZ;
ALTER TABLE contents ADD COLUMN brief_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE contents ADD COLUMN brief_generated_at TIMESTAMPTZ;
ALTER TABLE contents ADD COLUMN brief_mode VARCHAR(30);
ALTER TABLE contents ADD CONSTRAINT chk_contents_brief_status
    CHECK (brief_status IS NULL OR brief_status IN ('PENDING', 'READY', 'FAILED'));

CREATE INDEX ix_contents_brief_pending ON contents (brief_requested_at) WHERE brief_status = 'PENDING';
