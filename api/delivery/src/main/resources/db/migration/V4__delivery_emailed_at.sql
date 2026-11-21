-- Qué contenidos ya salieron por correo: un resumen nunca repite algo que el usuario ya recibió por mail.
ALTER TABLE deliveries ADD COLUMN emailed_at TIMESTAMPTZ;

-- Todo lo entregado hasta ahora ya formó parte de un resumen.
UPDATE deliveries SET emailed_at = delivered_at;

CREATE INDEX ix_deliveries_pending_email ON deliveries (user_id, delivered_at) WHERE emailed_at IS NULL;
