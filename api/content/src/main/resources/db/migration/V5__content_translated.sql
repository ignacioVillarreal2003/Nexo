-- Si el contenido ya tiene título y resumen en español y en inglés. Lo que la IA analizó con traducción ya lo está;
-- el resto (modo heurístico o anterior a la traducción) lo completa en segundo plano TranslationBackfillService.
ALTER TABLE contents ADD COLUMN translated BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE contents SET translated = TRUE WHERE title_en IS NOT NULL AND coalesce(language, 'es') LIKE 'es%';
CREATE INDEX ix_contents_untranslated ON contents (collected_at DESC) WHERE translated = FALSE AND status = 'PUBLISHED';
