# NEXO — Contenido personalizado diario

> *Lo nuevo de lo que te importa, cada día. Una vez, sin repetir.*

Nexo es una plataforma web en la que cada persona elige los tópicos que quiere seguir y recibe todos los días contenido **nuevo** sobre ellos. La plataforma recopila artículos, noticias e investigaciones de distintas fuentes. Una IA los clasifica, los resume y evalúa su relevancia, y un sistema antirrepetición garantiza que nadie reciba dos veces lo mismo.

La estructura del proyecto sigue la de Lumen: microservicios independientes en `api/`, la infraestructura y un compose por servicio en `api/docker/`, y la app Angular en `app/`.

## Estructura

```
api/
├── authentication/   Spring Boot · usuarios, JWT RS256 + JWKS, sesiones, roles y permisos, clientes de servicio, auditoría
├── notification/     Spring Boot · bandeja interna, correo (resumen diario), preferencias por categoría y canal
├── topic/            Spring Boot · categorías, tópicos, fuentes y plan de recopilación (catálogo cacheado en Redis)
├── subscription/     Spring Boot · suscripciones, límite diario por tópico, zona y hora de entrega
├── content/          Spring Boot · ingesta desde Kafka, análisis con IA, deduplicación en 3 niveles (pgvector)
├── delivery/         Spring Boot · entrega diaria por usuario, antirrepetición, Mi día, historial y favoritos
├── ai/               Python / FastAPI · Gemini (salida estructurada) + embeddings locales (sentence-transformers)
├── collector/        Python / FastAPI · recopilación programada y manual: RSS, Google News, arXiv, Hacker News, NewsAPI
└── docker/           infraestructura, un compose por servicio, observabilidad y scripts de inicialización
app/                  Angular 22 · Mi día, Explorar, Mis tópicos, Favoritos, Historial, Ajustes y Administración
```

Los servicios Java comparten la organización por capas de Lumen:

- `api`: configuración, controladores, filtros y seguridad.
- `application`: servicios y mappers de MapStruct.
- `domain`: entidades, DTOs, excepciones con `ErrorCode` y configuración.
- `infrastructure`: repositorios, clientes, mensajería y configuración técnica.

Las dependencias van en un solo sentido: `api` → `application` → `domain`, e `infrastructure` → `domain`. El dominio no conoce la web ni la infraestructura. Por eso los datos de la petición que viajan en el MDC (id de correlación, IP) están en `domain/util/RequestContext` y no en el filtro HTTP. Además:

- **Estados y modos como enums.** Por ejemplo `BriefStatus`, `AiMode` y `EmailRequestStatus`, en lugar de textos sueltos. El veredicto de la IA llega tipado como `ContentRelation`.
- **Parámetros de comportamiento en la configuración.** Cosas como cuántos informes por pasada, cuánto se espera a los informes o el tope de noticias por correo van en `ApplicationProperties`, con su valor por defecto en `application.yml` y una variable de entorno para cambiarlos.
- **Una sola responsabilidad por clase.** Por ejemplo, `UserProvisioner` es el único lugar donde se crea una cuenta; `NotificationCommandValidator` valida los comandos y `NotificationCommandProcessor` los entrega; `DigestComposer` arma el correo y `DigestDispatchService` decide cuándo sale.

Todas las respuestas usan el mismo envoltorio `ApiResponse` y los errores el mismo `ErrorResponse`.

## Cómo fluye el contenido

```
collector ──(Kafka: nexo.content.collected)──► content ──REST──► ai (Gemini + embeddings)
    ▲  plan (topic) y tópicos con suscriptores (subscription)          │
    │                                                        contenido publicado
    └──────────── token de servicio (client_credentials) ────────────  │
                                                                       ▼
subscription ◄──REST── delivery ──REST──► content (candidatos con embeddings)
                         │
                         └──(outbox → Kafka: nexo.notifications.commands)──► notification ──► correo e in-app
authentication ──(Kafka: nexo.authentication.user-events)──► notification, subscription, delivery
```

1. **Recopilación.** Por defecto corre a las 03:00 UTC; también se puede lanzar desde Admin. El collector pide al servicio `topic` el plan con los tópicos activos y sus fuentes, y se queda solo con los tópicos que tienen suscriptores. Normaliza cada documento, extrae el texto completo cuando hace falta y lo publica en Kafka.
2. **Procesamiento.** `content` consume cada documento y guarda el texto completo de la nota. Luego lo pasa por los tres niveles de deduplicación (ver más abajo). Antes del nivel semántico llama a la IA, que devuelve relevancia, calidad, título, resumen corto, palabras clave, entidades y el embedding. Si la IA cae, el documento se reintenta con backoff. Al agotarse los reintentos va al dead letter topic y queda registrado como error de esa ejecución.
3. **Entrega.** `delivery` corre cada hora y atiende a los usuarios cuya hora local de entrega coincide con la actual. Para cada tópico elige el mejor contenido que el usuario todavía no recibió y aplica la antirrepetición por usuario y los límites diarios.
4. **Informe completo.** De cada noticia elegida, `delivery` le pide a `content` el informe (`POST /api/v1/contents/briefs`). `content` lo genera en segundo plano con la IA (`POST /api/v1/briefs`). Si el texto guardado es corto, la IA entra a la nota original y la lee entera. Con eso escribe, en español y en inglés, un resumen completo en párrafos, los puntos clave y por qué importa. El informe se genera una sola vez por noticia y queda guardado en `content` y en cada entrega, y se ve en la app con "Resumen completo". Si la IA no tiene cuota durante más de 10 minutos, se arma un informe extractivo con el propio texto.
5. **Correo.** El resumen diario y "Enviarme por mail" se encolan (`email_requests`) y salen cuando las noticias tienen su informe, o a los 6 minutos con lo que haya. El aviso viaja por el outbox transaccional hacia `notification`, que arma el correo en el idioma de cada lector y lo muestra en la app. El correo trae, por noticia, la imagen, el medio, el tiempo de lectura, el resumen completo, lo clave, por qué importa y el enlace a la nota original.

### Antirrepetición

| Nivel | Dónde | Cómo |
|---|---|---|
| Exacto | content | SHA-256 de la URL normalizada (sin `utm_*`, `fbclid`, `www.`, fragmento ni barra final) y del texto normalizado |
| Textual | content | SimHash de 64 bits sobre shingles de 3 palabras; distancia de Hamming ≤ 3 en los últimos 14 días |
| Semántico | content | Embedding de 384 dimensiones, vecino más cercano en pgvector (HNSW). ≥ 0,92 cuenta como duplicado; entre 0,80 y 0,92 la IA decide si es **duplicado**, **actualización** o **nuevo**; por debajo de 0,80 es nuevo |
| Por usuario | delivery | `UNIQUE(user_id, content_id)`, más similitud con el historial del usuario de los últimos 60 días (≥ 0,88 se descarta). Una **actualización** de algo que ya recibió sí se entrega, marcada como tal |

Los umbrales se configuran con `SEMANTIC_DUPLICATE_THRESHOLD`, `SEMANTIC_RELATED_THRESHOLD` y `USER_SIMILARITY_THRESHOLD`.

### Seguridad entre servicios

- **Validación de tokens.** El servicio `authentication` firma los JWT con RS256 y publica su JWKS. Cada servicio valida el token por su cuenta.
- **Llamadas entre servicios.** Las hacen clientes de servicio (`client_credentials`) con scopes mínimos:

| Cliente | Scopes |
|---|---|
| collector | `TOPIC_PLAN_READ`, `SUBSCRIPTION_READ_UNRESTRICTED`, `CONTENT_INGEST` |
| content | `AI_ANALYZE` |
| delivery | `TOPIC_PLAN_READ`, `SUBSCRIPTION_READ_UNRESTRICTED`, `CONTENT_CANDIDATE_READ` |

  En `dev` vienen sembrados con secretos de desarrollo. En producción se crean desde **Admin → Clientes de servicio**.
- **Panel de administración.** Lo controlan los permisos `TOPIC_MANAGE`, `CONTENT_REVIEW`, `PIPELINE_READ`, `PIPELINE_MANAGE`, `DELIVERY_MANAGE` y `STATISTICS_READ`.

## Puesta en marcha

Los pasos están en [`api/docker/README.md`](api/docker/README.md). Resumen:

```bash
cd api/docker
cp .env.example .env
docker network create nexo
docker compose -f docker-compose.infrastructure.yml up -d --wait
bash ./scripts/init.sh
for s in authentication notification topic subscription ai content delivery collector; do
  docker compose -f docker-compose.$s.yml up -d --build
done

cd ../../app && npm install && npm start   # http://localhost:4300
```

Administrador inicial: `admin@nexo.local` / `Admin123!`. Se cambia con `ADMIN_EMAIL` y `ADMIN_PASSWORD`.

Sin `GEMINI_API_KEY`, el servicio de IA funciona en **modo heurístico**: hace resúmenes extractivos y calcula la relevancia con embeddings.

## Alcance

Esta versión cubre el MVP de la especificación:

- registro y sesión;
- catálogo de tópicos y suscripciones, con cantidad diaria;
- recopilación automática y procesamiento con IA;
- deduplicación;
- entrega diaria con informe completo de cada noticia (resumen en párrafos, puntos clave y por qué importa, en español e inglés);
- Mi día con historial y favoritos;
- notificaciones por correo (en el idioma de cada persona) e in-app;
- panel administrativo con catálogo, fuentes, revisión de contenidos, ejecuciones, errores y estadísticas.

Quedan para más adelante:

- la app móvil y las notificaciones push;
- las recomendaciones de tópicos según lo que cada persona lee;
- la interacción conversacional, los informes temáticos, el audio y los planes premium.
