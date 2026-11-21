-- Tres maneras de seguir noticias:
--   TOPIC   un tema (la IA descarta lo que no trata el tema);
--   COUNTRY las noticias principales de un país (su edición de Google News);
--   OUTLET  todo lo que publica un diario o sitio (su feed RSS).
-- En COUNTRY y OUTLET todo lo que llega es pertinente: minimum_relevance = 0 hace que content no filtre por tema
-- (sí sigue filtrando por calidad y deduplicando).
ALTER TABLE topics ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TOPIC';
ALTER TABLE topics ADD COLUMN country_code CHAR(2);
ALTER TABLE topics ADD CONSTRAINT chk_topics_kind CHECK (kind IN ('TOPIC', 'COUNTRY', 'OUTLET'));
CREATE INDEX ix_topics_kind ON topics (kind);

-- ---------------------------------------------------------------------------------------------- categorías nuevas
INSERT INTO categories (slug, name, description, icon, sort_order, created_by) VALUES
  ('deportes', 'Deportes', 'Fútbol, tenis, automovilismo y el resto del deporte.', 'target', 6, 'system'),
  ('politica', 'Política y sociedad', 'Gobiernos, elecciones y asuntos internacionales.', 'users', 7, 'system'),
  ('salud-bienestar', 'Salud y bienestar', 'Nutrición, salud mental y actividad física.', 'heart', 8, 'system'),
  ('medio-ambiente', 'Medio ambiente', 'Clima, energía y conservación.', 'sun', 9, 'system'),
  ('entretenimiento', 'Entretenimiento', 'Cine, series, música y videojuegos.', 'image', 10, 'system'),
  ('estilo-de-vida', 'Estilo de vida', 'Viajes y gastronomía.', 'plane', 11, 'system'),
  ('paises', 'Países', 'Las noticias principales de cada país.', 'globe', 20, 'system'),
  ('medios', 'Diarios y sitios', 'Todo lo que publica un medio en particular.', 'file-text', 21, 'system');

-- ---------------------------------------------------------------------------------------------- temas nuevos
INSERT INTO topics (category_id, slug, name, description, icon, keywords, search_query, created_by)
SELECT c.id, t.slug, t.name, t.description, t.icon, t.keywords, t.search_query, 'system'
FROM (VALUES
  ('deportes', 'futbol', 'Fútbol', 'Ligas, selecciones, fichajes y resultados.', 'target',
     '["fútbol", "gol", "liga", "selección", "Champions", "Copa Libertadores", "football", "soccer"]'::jsonb, 'fútbol OR football OR soccer'),
  ('deportes', 'tenis', 'Tenis', 'Circuitos ATP y WTA y los Grand Slams.', 'target',
     '["tenis", "ATP", "WTA", "Grand Slam", "Wimbledon", "Roland Garros", "tennis"]'::jsonb, 'tenis OR tennis OR ATP OR WTA'),
  ('deportes', 'formula-1', 'Fórmula 1', 'Carreras, equipos y pilotos.', 'target',
     '["Fórmula 1", "F1", "Gran Premio", "piloto", "Formula 1"]'::jsonb, '"Fórmula 1" OR "Formula 1" OR F1'),
  ('deportes', 'baloncesto', 'Básquet', 'NBA, Euroliga y las ligas locales.', 'target',
     '["básquet", "baloncesto", "NBA", "Euroliga", "basketball"]'::jsonb, 'básquet OR baloncesto OR NBA'),
  ('politica', 'politica-internacional', 'Política internacional', 'Relaciones entre países, conflictos y diplomacia.', 'globe',
     '["diplomacia", "ONU", "cumbre", "conflicto", "sanciones", "geopolítica"]'::jsonb, 'política internacional OR geopolítica OR diplomacia'),
  ('politica', 'elecciones', 'Elecciones', 'Campañas, encuestas y resultados electorales.', 'users',
     '["elecciones", "votación", "encuesta", "candidato", "campaña electoral", "elections"]'::jsonb, 'elecciones OR elections OR encuesta electoral'),
  ('salud-bienestar', 'nutricion', 'Nutrición', 'Alimentación saludable y lo que dice la ciencia.', 'heart',
     '["nutrición", "dieta", "alimentación", "nutrition", "diet"]'::jsonb, 'nutrición OR alimentación saludable OR nutrition'),
  ('salud-bienestar', 'salud-mental', 'Salud mental', 'Bienestar emocional, ansiedad y terapia.', 'brain',
     '["salud mental", "ansiedad", "depresión", "terapia", "mental health"]'::jsonb, '"salud mental" OR "mental health"'),
  ('salud-bienestar', 'actividad-fisica', 'Actividad física', 'Entrenamiento, running y deporte saludable.', 'heart',
     '["ejercicio", "entrenamiento", "running", "fitness", "actividad física"]'::jsonb, 'ejercicio OR entrenamiento OR fitness'),
  ('medio-ambiente', 'cambio-climatico', 'Cambio climático', 'Calentamiento global, emisiones y adaptación.', 'sun',
     '["cambio climático", "calentamiento global", "emisiones", "COP", "climate change"]'::jsonb, '"cambio climático" OR "climate change"'),
  ('medio-ambiente', 'energias-renovables', 'Energías renovables', 'Solar, eólica, baterías y transición energética.', 'sun',
     '["energía solar", "eólica", "renovables", "transición energética", "renewable energy"]'::jsonb, 'energías renovables OR "renewable energy" OR energía solar'),
  ('entretenimiento', 'cine-series', 'Cine y series', 'Estrenos, plataformas y premios.', 'image',
     '["cine", "película", "serie", "estreno", "Netflix", "Oscar"]'::jsonb, 'cine OR película OR serie estreno'),
  ('entretenimiento', 'musica', 'Música', 'Lanzamientos, giras y la industria musical.', 'sparkles',
     '["música", "álbum", "gira", "concierto", "canción", "music"]'::jsonb, 'música OR álbum OR gira concierto'),
  ('entretenimiento', 'videojuegos', 'Videojuegos', 'Lanzamientos, consolas y la industria del gaming.', 'command',
     '["videojuegos", "PlayStation", "Xbox", "Nintendo", "gaming", "videogames"]'::jsonb, 'videojuegos OR gaming OR PlayStation OR Nintendo'),
  ('estilo-de-vida', 'viajes', 'Viajes', 'Destinos, turismo y consejos para viajar.', 'plane',
     '["viajes", "turismo", "destino", "vuelos", "travel"]'::jsonb, 'viajes OR turismo OR destinos'),
  ('estilo-de-vida', 'gastronomia', 'Gastronomía', 'Restaurantes, recetas y tendencias culinarias.', 'utensils',
     '["gastronomía", "restaurante", "receta", "chef", "cocina"]'::jsonb, 'gastronomía OR restaurante OR chef')
) AS t(category, slug, name, description, icon, keywords, search_query)
JOIN categories c ON c.slug = t.category;

-- Las búsquedas de Google News cubren todos los temas nuevos (en inglés, los que tienen cobertura internacional).
INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t JOIN categories c ON c.id = t.category_id, sources s
WHERE s.name = 'Google News (ES)'
  AND c.slug IN ('deportes', 'politica', 'salud-bienestar', 'medio-ambiente', 'entretenimiento', 'estilo-de-vida');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s
WHERE s.name = 'Google News (EN)'
  AND t.slug IN ('tenis', 'formula-1', 'baloncesto', 'politica-internacional', 'cambio-climatico',
                 'energias-renovables', 'cine-series', 'videojuegos');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s
WHERE s.name = 'ScienceDaily · Salud' AND t.slug IN ('nutricion', 'salud-mental', 'actividad-fisica');

-- ---------------------------------------------------------------------------------------------- países
-- Cada país lee los titulares de su edición de Google News.
INSERT INTO sources (name, type, url, configuration, created_by) VALUES
  ('Google News · Argentina', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=AR&ceid=AR:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · Uruguay', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=UY&ceid=UY:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · Chile', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=CL&ceid=CL:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · Colombia', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=CO&ceid=CO:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · México', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=MX&ceid=MX:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · Perú', 'RSS', 'https://news.google.com/rss?hl=es-419&gl=PE&ceid=PE:es-419', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · España', 'RSS', 'https://news.google.com/rss?hl=es&gl=ES&ceid=ES:es', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Google News · Estados Unidos', 'RSS', 'https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en', '{"maxItems": 25, "language": "en"}'::jsonb, 'system');

INSERT INTO topics (category_id, slug, name, description, icon, keywords, kind, country_code, minimum_relevance,
                    maximum_items_per_run, created_by)
SELECT c.id, t.slug, t.name, t.description, 'globe', t.keywords, 'COUNTRY', t.country, 0, 30, 'system'
FROM (VALUES
  ('pais-argentina', 'Argentina', 'Lo más importante del día en Argentina.', '["Argentina"]'::jsonb, 'AR'),
  ('pais-uruguay', 'Uruguay', 'Lo más importante del día en Uruguay.', '["Uruguay"]'::jsonb, 'UY'),
  ('pais-chile', 'Chile', 'Lo más importante del día en Chile.', '["Chile"]'::jsonb, 'CL'),
  ('pais-colombia', 'Colombia', 'Lo más importante del día en Colombia.', '["Colombia"]'::jsonb, 'CO'),
  ('pais-mexico', 'México', 'Lo más importante del día en México.', '["México"]'::jsonb, 'MX'),
  ('pais-peru', 'Perú', 'Lo más importante del día en Perú.', '["Perú"]'::jsonb, 'PE'),
  ('pais-espana', 'España', 'Lo más importante del día en España.', '["España"]'::jsonb, 'ES'),
  ('pais-estados-unidos', 'Estados Unidos', 'Lo más importante del día en Estados Unidos.', '["Estados Unidos", "United States"]'::jsonb, 'US')
) AS t(slug, name, description, keywords, country)
JOIN categories c ON c.slug = 'paises';

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id
FROM (VALUES
  ('pais-argentina', 'Google News · Argentina'), ('pais-uruguay', 'Google News · Uruguay'),
  ('pais-chile', 'Google News · Chile'), ('pais-colombia', 'Google News · Colombia'),
  ('pais-mexico', 'Google News · México'), ('pais-peru', 'Google News · Perú'),
  ('pais-espana', 'Google News · España'), ('pais-estados-unidos', 'Google News · Estados Unidos')
) AS m(topic_slug, source_name)
JOIN topics t ON t.slug = m.topic_slug
JOIN sources s ON s.name = m.source_name;

-- ---------------------------------------------------------------------------------------------- diarios y sitios
-- Feeds verificados al escribir esta migración. BBC Mundo ya existía como fuente (V4) y se reutiliza.
INSERT INTO sources (name, type, url, configuration, created_by) VALUES
  ('La Nación', 'RSS', 'https://www.lanacion.com.ar/arc/outboundfeeds/rss/?outputType=xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Clarín', 'RSS', 'https://www.clarin.com/rss/lo-ultimo/', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Infobae', 'RSS', 'https://www.infobae.com/arc/outboundfeeds/rss/', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('Montevideo Portal', 'RSS', 'https://www.montevideo.com.uy/anxml.aspx?59', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('El País Uruguay', 'RSS', 'https://www.elpais.com.uy/rss/', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('El País', 'RSS', 'https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('El Mundo', 'RSS', 'https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('El Universal', 'RSS', 'https://www.eluniversal.com.mx/arc/outboundfeeds/rss/?outputType=xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('El Tiempo', 'RSS', 'https://www.eltiempo.com/rss/colombia.xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('La Tercera', 'RSS', 'https://www.latercera.com/arc/outboundfeeds/rss/?outputType=xml', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('DW Español', 'RSS', 'https://rss.dw.com/xml/rss-sp-all', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('France 24 Español', 'RSS', 'https://www.france24.com/es/rss', '{"maxItems": 25, "language": "es"}'::jsonb, 'system'),
  ('The Guardian', 'RSS', 'https://www.theguardian.com/world/rss', '{"maxItems": 25, "language": "en"}'::jsonb, 'system'),
  ('The New York Times', 'RSS', 'https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml', '{"maxItems": 25, "language": "en"}'::jsonb, 'system'),
  ('BBC News', 'RSS', 'https://feeds.bbci.co.uk/news/rss.xml', '{"maxItems": 25, "language": "en"}'::jsonb, 'system');

INSERT INTO topics (category_id, slug, name, description, icon, keywords, kind, country_code, minimum_relevance,
                    maximum_items_per_run, created_by)
SELECT c.id, t.slug, t.name, t.description, 'file-text', t.keywords, 'OUTLET', t.country, 0, 30, 'system'
FROM (VALUES
  ('medio-la-nacion', 'La Nación', 'Diario argentino: política, economía, sociedad y deportes.', '["La Nación"]'::jsonb, 'AR'),
  ('medio-clarin', 'Clarín', 'Diario argentino: lo último en noticias.', '["Clarín"]'::jsonb, 'AR'),
  ('medio-infobae', 'Infobae', 'Portal de noticias argentino con cobertura regional.', '["Infobae"]'::jsonb, 'AR'),
  ('medio-montevideo-portal', 'Montevideo Portal', 'Portal de noticias uruguayo.', '["Montevideo Portal"]'::jsonb, 'UY'),
  ('medio-el-pais-uy', 'El País Uruguay', 'Diario uruguayo.', '["El País"]'::jsonb, 'UY'),
  ('medio-el-pais', 'El País', 'Diario español, portada.', '["El País"]'::jsonb, 'ES'),
  ('medio-el-mundo', 'El Mundo', 'Diario español, portada.', '["El Mundo"]'::jsonb, 'ES'),
  ('medio-el-universal', 'El Universal', 'Diario mexicano.', '["El Universal"]'::jsonb, 'MX'),
  ('medio-el-tiempo', 'El Tiempo', 'Diario colombiano.', '["El Tiempo"]'::jsonb, 'CO'),
  ('medio-la-tercera', 'La Tercera', 'Diario chileno.', '["La Tercera"]'::jsonb, 'CL'),
  ('medio-bbc-mundo', 'BBC Mundo', 'El servicio en español de la BBC.', '["BBC Mundo"]'::jsonb, 'GB'),
  ('medio-dw', 'DW Español', 'La cadena alemana Deutsche Welle, en español.', '["DW"]'::jsonb, 'DE'),
  ('medio-france-24', 'France 24 Español', 'La cadena francesa France 24, en español.', '["France 24"]'::jsonb, 'FR'),
  ('medio-the-guardian', 'The Guardian', 'Diario británico, sección internacional (en inglés).', '["The Guardian"]'::jsonb, 'GB'),
  ('medio-nyt', 'The New York Times', 'Diario estadounidense, portada (en inglés).', '["The New York Times"]'::jsonb, 'US'),
  ('medio-bbc-news', 'BBC News', 'Noticias de la BBC (en inglés).', '["BBC News"]'::jsonb, 'GB')
) AS t(slug, name, description, keywords, country)
JOIN categories c ON c.slug = 'medios';

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id
FROM (VALUES
  ('medio-la-nacion', 'La Nación'), ('medio-clarin', 'Clarín'), ('medio-infobae', 'Infobae'),
  ('medio-montevideo-portal', 'Montevideo Portal'), ('medio-el-pais-uy', 'El País Uruguay'),
  ('medio-el-pais', 'El País'), ('medio-el-mundo', 'El Mundo'), ('medio-el-universal', 'El Universal'),
  ('medio-el-tiempo', 'El Tiempo'), ('medio-la-tercera', 'La Tercera'), ('medio-bbc-mundo', 'BBC Mundo'),
  ('medio-dw', 'DW Español'), ('medio-france-24', 'France 24 Español'), ('medio-the-guardian', 'The Guardian'),
  ('medio-nyt', 'The New York Times'), ('medio-bbc-news', 'BBC News')
) AS m(topic_slug, source_name)
JOIN topics t ON t.slug = m.topic_slug
JOIN sources s ON s.name = m.source_name;
