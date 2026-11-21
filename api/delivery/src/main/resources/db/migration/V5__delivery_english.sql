-- Copia en inglés del título y el resumen entregados, para mostrar cada noticia en el idioma del lector.
ALTER TABLE deliveries ADD COLUMN title_en VARCHAR(500);
ALTER TABLE deliveries ADD COLUMN summary_en VARCHAR(5000);
