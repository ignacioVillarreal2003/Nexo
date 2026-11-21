-- ShedLock con usingDbTime() escribe timezone('utc', now()): un TIMESTAMP sin zona.
-- Guardado en TIMESTAMPTZ, Postgres lo interpreta con la zona de la sesión, así que un proceso
-- con zona distinta de UTC (por ejemplo, la JVM corriendo fuera de Docker) dejaba el lock corrido
-- varias horas y bloqueaba las tareas programadas. Se guardan como TIMESTAMP en UTC.
ALTER TABLE shedlock
    ALTER COLUMN lock_until TYPE TIMESTAMP USING lock_until AT TIME ZONE 'UTC',
    ALTER COLUMN locked_at  TYPE TIMESTAMP USING locked_at  AT TIME ZONE 'UTC';

-- Libera locks que quedaron en el futuro por ese corrimiento.
UPDATE shedlock SET lock_until = timezone('utc', now()) WHERE lock_until > timezone('utc', now()) + INTERVAL '10 minutes';
