# Nexo — Stacks de Docker

Todos los `docker-compose` de la plataforma viven acá, separados por responsabilidad y unidos por una red externa de Docker (`nexo`). Los puertos del host son distintos de los de Lumen, así que las dos plataformas pueden correr a la vez.

| Archivo | Contenido | Puertos del host |
| --- | --- | --- |
| `docker-compose.infrastructure.yml` | PostgreSQL 17 + pgvector (una base por servicio), Redis, Kafka | 5433, 6380, 29093 |
| `docker-compose.observability.yml` | Prometheus, Alertmanager, Tempo y Grafana (opcional) | 9190, 9193, 3210/4327/4328, 3011 |
| `docker-compose.authentication.yml` | API de autenticación | 8090 |
| `docker-compose.notification.yml` | API de notificaciones (in-app + correo) | 8091 |
| `docker-compose.topic.yml` | Catálogo de tópicos y fuentes | 8092 |
| `docker-compose.subscription.yml` | Suscripciones y configuración de entrega | 8093 |
| `docker-compose.content.yml` | Ingesta, IA y deduplicación | 8094 |
| `docker-compose.delivery.yml` | Entregas diarias, Mi día e historial | 8095 |
| `docker-compose.ai.yml` | Servicio de IA (FastAPI) | 8096 |
| `docker-compose.collector.yml` | Recopilación (FastAPI) | 8097 |

Cada API tiene su `Dockerfile` en su directorio (`api/<servicio>`). Estos composes solo lo construyen (`build: ../<servicio>`).

## 1. Red y configuración (una sola vez)

```bash
docker network create nexo
cp .env.example .env
```

Los composes tienen un valor por defecto para cada variable. Los scripts de `scripts/` en cambio necesitan el `.env`, porque de ahí sacan los nombres de las bases.

## 2. Levantar todo, en orden

```bash
# Infraestructura (espera a que quede healthy)
docker compose -f docker-compose.infrastructure.yml up -d --wait

# Bases y extensión pgvector (idempotente)
bash ./scripts/init.sh

# APIs
# De a una, en este orden. No combines varios -f en un mismo comando: Compose toma el `name:`
# del último archivo como proyecto y después los contenedores chocan al recrearlos por separado.
for s in authentication notification topic subscription ai content delivery collector; do
  docker compose -f docker-compose.$s.yml up -d --build
done

# Observabilidad (opcional). Para enviar trazas a Tempo, poné TRACING_ENABLED=true en .env
# y recreá las APIs; con false (por defecto) no se intenta exportar nada.
docker compose -f docker-compose.observability.yml up -d
```

El orden importa por estas razones:

- Todos los servicios validan los JWT contra el JWKS de `authentication`.
- `content` crea el topic `nexo.content.collected`, al que publica el collector. Kafka no crea topics automáticamente.
- `content` y `delivery` piden su token de servicio a `authentication`.

La primera vez, `ai` descarga el modelo de embeddings (unos 470 MB) en el volumen `ai-models`.

## 3. Frontend

El frontend no está dockerizado; se corre aparte:

```bash
cd ../../app
npm install
npm start   # http://localhost:4300
```

## 4. Probar el flujo completo

1. Entrá con `admin@nexo.local` / `Admin123!`, o registrá un usuario. En `dev` no se exige verificar el correo.
2. En **Explorar**, seguí algunos tópicos.
3. En **Admin → Resumen**, tocá **Recopilar ahora**. Las ejecuciones y los errores se ven en esa misma pantalla.
4. Cuando haya contenido publicado, tocá **Buscar noticias** en **Mi día**, o **Entregar a todos** desde el panel. El resumen diario llega por correo y a la campana de notificaciones.

## Servicios externos

- **Correo (Gmail).** Los correos (verificación, resumen diario, "Enviarme por mail") salen por SMTP real. Cargá en
  `.env` `MAIL_USERNAME` y `EMAIL_FROM` con la cuenta de Gmail que envía y `MAIL_PASSWORD` con una contraseña de
  aplicación de esa cuenta (https://myaccount.google.com/apppasswords). Después recreá `notification`.

- **Gemini.** Para usarlo, cargá `GEMINI_API_KEY` en `.env` (clave gratuita en https://aistudio.google.com). Sin la clave, la IA funciona en modo heurístico.
- **NewsAPI.** Para activar la fuente NewsAPI, cargá `NEWSAPI_KEY` y activala en **Admin → Fuentes**.
