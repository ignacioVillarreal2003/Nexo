-- La traducción en segundo plano se reemplazó por el informe completo (que trae los dos idiomas).
DROP INDEX IF EXISTS ix_contents_untranslated;
ALTER TABLE contents DROP COLUMN translated;
