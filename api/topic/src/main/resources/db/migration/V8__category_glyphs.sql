-- Íconos propios de Nexo: cada categoría tiene uno dibujado (el frontend lo resuelve a partir de "cat:<slug>")
-- y sus tópicos usan el de su categoría, así todo el catálogo se ve parejo.
UPDATE categories SET icon = 'cat:' || slug;
UPDATE topics t SET icon = 'cat:' || c.slug FROM categories c WHERE c.id = t.category_id;
