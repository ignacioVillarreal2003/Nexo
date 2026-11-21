-- "Enviarme por mail" es un pedido explícito del lector: va en su propia categoría obligatoria, así se manda aunque
-- haya apagado el correo del resumen diario automático.
INSERT INTO notification_categories (code, name, description, is_mandatory, is_system)
VALUES ('DELIVERY_ON_DEMAND', 'Requested digest',
        'The news of your day, sent when you ask for it with "Email it to me".', true, true)
ON CONFLICT (code) DO NOTHING;

-- Nadie envía CONTENT_UPDATES: las actualizaciones de una noticia llegan dentro del resumen diario.
DELETE FROM notification_category_channel_preferences WHERE category = 'CONTENT_UPDATES';
UPDATE email_outbox SET category = NULL WHERE category = 'CONTENT_UPDATES';
DELETE FROM notification_categories
WHERE code = 'CONTENT_UPDATES'
  AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.category = 'CONTENT_UPDATES');
