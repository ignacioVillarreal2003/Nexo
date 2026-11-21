-- La misma noticia en inglés (la escribe la IA junto con la versión principal), para mostrarla en el idioma del
-- lector. Vacío cuando no hay traducción (modo heurístico o contenido anterior): se muestra la principal.
ALTER TABLE contents ADD COLUMN title_en VARCHAR(500);
ALTER TABLE contents ADD COLUMN summary_en VARCHAR(5000);
