-- Feeds directos de medios especializados: traen descripción o texto completo y mejoran los resúmenes
-- frente a los agregadores (Google News solo expone el titular).
INSERT INTO sources (name, type, url, configuration, created_by) VALUES
  ('Ars Technica', 'RSS', 'https://feeds.arstechnica.com/arstechnica/index', '{"maxItems": 20, "language": "en"}'::jsonb, 'system'),
  ('TechCrunch', 'RSS', 'https://techcrunch.com/feed/', '{"maxItems": 20, "language": "en"}'::jsonb, 'system'),
  ('MIT Technology Review', 'RSS', 'https://www.technologyreview.com/feed/', '{"maxItems": 15, "language": "en"}'::jsonb, 'system'),
  ('ScienceDaily', 'RSS', 'https://www.sciencedaily.com/rss/all.xml', '{"maxItems": 25, "language": "en"}'::jsonb, 'system'),
  ('ScienceDaily · Espacio', 'RSS', 'https://www.sciencedaily.com/rss/space_time.xml', '{"maxItems": 15, "language": "en"}'::jsonb, 'system'),
  ('ScienceDaily · Mente y cerebro', 'RSS', 'https://www.sciencedaily.com/rss/mind_brain.xml', '{"maxItems": 15, "language": "en"}'::jsonb, 'system'),
  ('ScienceDaily · Salud', 'RSS', 'https://www.sciencedaily.com/rss/health_medicine.xml', '{"maxItems": 15, "language": "en"}'::jsonb, 'system'),
  ('Phys.org', 'RSS', 'https://phys.org/rss-feed/', '{"maxItems": 20, "language": "en"}'::jsonb, 'system'),
  ('BBC Mundo', 'RSS', 'https://feeds.bbci.co.uk/mundo/rss.xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('CoinDesk', 'RSS', 'https://www.coindesk.com/arc/outboundfeeds/rss/', '{"maxItems": 15, "language": "en"}'::jsonb, 'system');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id
FROM (VALUES
  ('Ars Technica', 'desarrollo-software'), ('Ars Technica', 'ciberseguridad'), ('Ars Technica', 'hardware'),
  ('Ars Technica', 'inteligencia-artificial'), ('Ars Technica', 'cloud-computing'),
  ('TechCrunch', 'startups'), ('TechCrunch', 'inteligencia-artificial'), ('TechCrunch', 'innovacion'),
  ('MIT Technology Review', 'inteligencia-artificial'), ('MIT Technology Review', 'robotica'),
  ('MIT Technology Review', 'innovacion'), ('MIT Technology Review', 'ciencia-datos'),
  ('ScienceDaily', 'biologia'), ('ScienceDaily', 'fisica'),
  ('ScienceDaily · Espacio', 'astronomia'), ('ScienceDaily · Mente y cerebro', 'neurociencia'),
  ('ScienceDaily · Mente y cerebro', 'psicologia'), ('ScienceDaily · Salud', 'medicina-investigacion'),
  ('Phys.org', 'fisica'), ('Phys.org', 'astronomia'), ('Phys.org', 'biologia'),
  ('BBC Mundo', 'economia-global'), ('BBC Mundo', 'historia'), ('BBC Mundo', 'geografia'),
  ('BBC Mundo', 'medicina-investigacion'),
  ('CoinDesk', 'criptomonedas')
) AS m(source_name, topic_slug)
JOIN sources s ON s.name = m.source_name
JOIN topics t ON t.slug = m.topic_slug;
