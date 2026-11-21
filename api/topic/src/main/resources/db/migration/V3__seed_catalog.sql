-- Catálogo inicial (sección 4 de la especificación).
INSERT INTO categories (slug, name, description, icon, sort_order, created_by) VALUES
  ('tecnologia', 'Tecnología', 'Software, IA, seguridad y el hardware que mueve el mundo digital.', 'memory', 1, 'system'),
  ('ciencia', 'Ciencia', 'Descubrimientos e investigación en ciencias naturales y de la salud.', 'science', 2, 'system'),
  ('economia-finanzas', 'Economía y finanzas', 'Macroeconomía, mercados e inversión personal.', 'trending_up', 3, 'system'),
  ('negocios', 'Negocios', 'Emprendimiento, startups, innovación y gestión.', 'business_center', 4, 'system'),
  ('cultura-conocimiento', 'Cultura y conocimiento', 'Humanidades, pensamiento y el mundo que nos rodea.', 'menu_book', 5, 'system');

INSERT INTO topics (category_id, slug, name, description, icon, keywords, search_query, arxiv_categories, created_by)
SELECT c.id, t.slug, t.name, t.description, t.icon, t.keywords, t.search_query, t.arxiv, 'system'
FROM (VALUES
  ('tecnologia', 'inteligencia-artificial', 'Inteligencia artificial', 'Modelos, investigación y aplicaciones de la IA.', 'smart_toy',
     '["inteligencia artificial", "machine learning", "LLM", "deep learning", "artificial intelligence"]'::jsonb, 'inteligencia artificial OR "artificial intelligence" OR LLM', 'cs.AI cs.LG cs.CL'),
  ('tecnologia', 'desarrollo-software', 'Desarrollo de software', 'Lenguajes, frameworks, arquitectura y prácticas de ingeniería.', 'code',
     '["programación", "software", "framework", "open source", "developer", "programming"]'::jsonb, 'desarrollo de software OR programming language OR framework release', 'cs.SE'),
  ('tecnologia', 'ciberseguridad', 'Ciberseguridad', 'Vulnerabilidades, ataques, defensa y privacidad.', 'security',
     '["ciberseguridad", "vulnerabilidad", "ransomware", "malware", "cybersecurity", "CVE"]'::jsonb, 'ciberseguridad OR cybersecurity OR ransomware', 'cs.CR'),
  ('tecnologia', 'cloud-computing', 'Cloud computing', 'Nubes públicas, infraestructura y plataformas.', 'cloud',
     '["cloud", "nube", "AWS", "Azure", "Google Cloud", "kubernetes"]'::jsonb, 'cloud computing OR AWS OR Azure OR kubernetes', 'cs.DC'),
  ('tecnologia', 'hardware', 'Hardware', 'Procesadores, GPUs, dispositivos y semiconductores.', 'developer_board',
     '["hardware", "procesador", "GPU", "chip", "semiconductores", "CPU"]'::jsonb, 'semiconductores OR GPU OR procesador OR chip', 'cs.AR'),
  ('tecnologia', 'robotica', 'Robótica', 'Robots, automatización y sistemas autónomos.', 'precision_manufacturing',
     '["robótica", "robot", "automatización", "humanoide", "robotics"]'::jsonb, 'robótica OR robotics OR robot humanoide', 'cs.RO'),
  ('tecnologia', 'ciencia-datos', 'Ciencia de datos', 'Análisis, estadística, visualización y big data.', 'insights',
     '["ciencia de datos", "data science", "big data", "analítica", "estadística"]'::jsonb, 'ciencia de datos OR "data science" OR analytics', 'stat.ML cs.DB'),

  ('ciencia', 'fisica', 'Física', 'De partículas a cosmología: avances de la física.', 'bolt',
     '["física", "partículas", "cuántica", "physics", "quantum"]'::jsonb, 'física OR physics OR cuántica', 'physics quant-ph hep-ph'),
  ('ciencia', 'astronomia', 'Astronomía', 'Exploración espacial, telescopios y el universo.', 'rocket_launch',
     '["astronomía", "NASA", "telescopio", "exoplaneta", "espacio", "astronomy"]'::jsonb, 'astronomía OR NASA OR telescopio OR exoplaneta', 'astro-ph'),
  ('ciencia', 'biologia', 'Biología', 'Vida, genética, evolución y ecología.', 'biotech',
     '["biología", "genética", "evolución", "ADN", "biology"]'::jsonb, 'biología OR genética OR evolución', 'q-bio'),
  ('ciencia', 'medicina-investigacion', 'Medicina e investigación', 'Ensayos clínicos, tratamientos y salud pública.', 'medical_services',
     '["medicina", "ensayo clínico", "tratamiento", "salud", "vacuna", "medical research"]'::jsonb, 'investigación médica OR ensayo clínico OR tratamiento', NULL),
  ('ciencia', 'neurociencia', 'Neurociencia', 'Cerebro, cognición y sistema nervioso.', 'psychology',
     '["neurociencia", "cerebro", "neuronas", "cognición", "neuroscience"]'::jsonb, 'neurociencia OR cerebro OR neuroscience', 'q-bio.NC'),

  ('economia-finanzas', 'economia-global', 'Economía global', 'Crecimiento, inflación, comercio y bancos centrales.', 'public',
     '["economía", "inflación", "PIB", "banco central", "comercio internacional"]'::jsonb, 'economía mundial OR inflación OR banco central', NULL),
  ('economia-finanzas', 'mercados-financieros', 'Mercados financieros', 'Bolsas, bonos, divisas y materias primas.', 'candlestick_chart',
     '["bolsa", "mercados", "bonos", "Wall Street", "divisas"]'::jsonb, 'mercados financieros OR Wall Street OR bolsa', NULL),
  ('economia-finanzas', 'acciones-etfs', 'Acciones y ETFs', 'Empresas cotizadas, resultados y fondos indexados.', 'show_chart',
     '["acciones", "ETF", "resultados trimestrales", "S&P 500", "dividendos"]'::jsonb, 'acciones OR ETF OR resultados trimestrales', NULL),
  ('economia-finanzas', 'criptomonedas', 'Criptomonedas', 'Bitcoin, Ethereum, blockchain y regulación.', 'currency_bitcoin',
     '["bitcoin", "ethereum", "cripto", "blockchain", "stablecoin"]'::jsonb, 'bitcoin OR ethereum OR criptomonedas', NULL),
  ('economia-finanzas', 'finanzas-personales', 'Finanzas personales', 'Ahorro, presupuesto, deudas e inversión cotidiana.', 'savings',
     '["finanzas personales", "ahorro", "presupuesto", "inversión", "jubilación"]'::jsonb, 'finanzas personales OR ahorro OR presupuesto familiar', NULL),

  ('negocios', 'emprendimientos', 'Emprendimientos', 'Historias, herramientas y aprendizajes para emprender.', 'lightbulb',
     '["emprendimiento", "emprendedor", "pyme", "negocio propio"]'::jsonb, 'emprendimiento OR emprendedores OR pymes', NULL),
  ('negocios', 'startups', 'Startups', 'Rondas de inversión, ecosistema y capital de riesgo.', 'rocket',
     '["startup", "venture capital", "ronda de inversión", "unicornio"]'::jsonb, 'startup OR "venture capital" OR ronda de inversión', NULL),
  ('negocios', 'innovacion', 'Innovación', 'Nuevos productos, I+D y transformación digital.', 'auto_awesome',
     '["innovación", "transformación digital", "I+D", "disrupción"]'::jsonb, 'innovación OR transformación digital', NULL),
  ('negocios', 'marketing', 'Marketing', 'Marcas, publicidad, redes sociales y crecimiento.', 'campaign',
     '["marketing", "publicidad", "marca", "redes sociales", "SEO"]'::jsonb, 'marketing OR publicidad OR marcas', NULL),
  ('negocios', 'gestion-empresarial', 'Gestión empresarial', 'Liderazgo, estrategia, equipos y operaciones.', 'groups',
     '["liderazgo", "gestión", "estrategia empresarial", "management", "trabajo remoto"]'::jsonb, 'liderazgo empresarial OR estrategia empresarial OR management', NULL),

  ('cultura-conocimiento', 'historia', 'Historia', 'Arqueología, historiografía y efemérides.', 'account_balance',
     '["historia", "arqueología", "histórico", "siglo"]'::jsonb, 'historia OR arqueología OR descubrimiento histórico', NULL),
  ('cultura-conocimiento', 'filosofia', 'Filosofía', 'Ética, pensamiento contemporáneo y clásicos.', 'self_improvement',
     '["filosofía", "ética", "filósofo", "pensamiento"]'::jsonb, 'filosofía OR ética OR filósofo', NULL),
  ('cultura-conocimiento', 'psicologia', 'Psicología', 'Mente, conducta, bienestar y salud mental.', 'favorite',
     '["psicología", "salud mental", "conducta", "bienestar", "ansiedad"]'::jsonb, 'psicología OR salud mental', NULL),
  ('cultura-conocimiento', 'geografia', 'Geografía', 'Territorio, clima, población y mapas.', 'map',
     '["geografía", "clima", "territorio", "población", "mapa"]'::jsonb, 'geografía OR territorio OR cambio climático', NULL),
  ('cultura-conocimiento', 'literatura', 'Literatura', 'Libros, autores, premios y crítica literaria.', 'auto_stories',
     '["literatura", "novela", "libro", "escritor", "premio literario"]'::jsonb, 'literatura OR novela OR premio literario', NULL)
) AS t(category, slug, name, description, icon, keywords, search_query, arxiv)
JOIN categories c ON c.slug = t.category;

-- Fuentes iniciales.
INSERT INTO sources (name, type, url, configuration, created_by) VALUES
  ('Google News (ES)', 'RSS_SEARCH', 'https://news.google.com/rss/search?q={query}+when:2d&hl=es-419&gl=AR&ceid=AR:es-419', '{"maxItems": 20}'::jsonb, 'system'),
  ('Google News (EN)', 'RSS_SEARCH', 'https://news.google.com/rss/search?q={query}+when:2d&hl=en-US&gl=US&ceid=US:en', '{"maxItems": 15}'::jsonb, 'system'),
  ('arXiv', 'ARXIV', 'https://export.arxiv.org/api/query', '{"maxItems": 10}'::jsonb, 'system'),
  ('Hacker News', 'HACKERNEWS', 'https://hn.algolia.com/api/v1/search_by_date', '{"maxItems": 15, "minPoints": 20}'::jsonb, 'system'),
  ('NASA Breaking News', 'RSS', 'https://www.nasa.gov/news-release/feed/', '{"maxItems": 15}'::jsonb, 'system'),
  ('Krebs on Security', 'RSS', 'https://krebsonsecurity.com/feed/', '{"maxItems": 10}'::jsonb, 'system'),
  ('The Hacker News', 'RSS', 'https://feeds.feedburner.com/TheHackersNews', '{"maxItems": 15}'::jsonb, 'system'),
  ('NewsAPI', 'NEWSAPI', 'https://newsapi.org/v2/everything', '{"maxItems": 20, "language": "es"}'::jsonb, 'system');

-- Google News en español para todos los tópicos.
INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s WHERE s.name = 'Google News (ES)';

-- Google News en inglés para tecnología, ciencia, negocios y mercados.
INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t JOIN categories c ON c.id = t.category_id, sources s
WHERE s.name = 'Google News (EN)' AND c.slug IN ('tecnologia', 'ciencia', 'negocios', 'economia-finanzas');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s WHERE s.name = 'arXiv' AND t.arxiv_categories IS NOT NULL
  AND t.slug IN ('inteligencia-artificial', 'fisica', 'astronomia', 'neurociencia', 'robotica', 'ciencia-datos');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t JOIN categories c ON c.id = t.category_id, sources s
WHERE s.name = 'Hacker News' AND (c.slug = 'tecnologia' OR t.slug = 'startups');

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s WHERE s.name = 'NASA Breaking News' AND t.slug = 'astronomia';

INSERT INTO topic_sources (topic_id, source_id)
SELECT t.id, s.id FROM topics t, sources s
WHERE s.name IN ('Krebs on Security', 'The Hacker News') AND t.slug = 'ciberseguridad';

-- NewsAPI requiere clave: queda inactiva hasta configurarla.
UPDATE sources SET status = 'INACTIVE' WHERE type = 'NEWSAPI';
