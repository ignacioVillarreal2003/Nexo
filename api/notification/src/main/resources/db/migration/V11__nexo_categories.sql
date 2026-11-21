-- Nexo no tiene espacios de documentos ni de finanzas: se reemplazan sus categorías
-- por las de la entrega diaria de contenido.
DELETE FROM notification_categories
WHERE code LIKE 'DOCUMENT_WORKSPACE_%' OR code LIKE 'FINANCE_WORKSPACE_%';

INSERT INTO notification_categories (code, name, description, is_mandatory, is_system)
VALUES ('DELIVERY_DAILY_DIGEST', 'Daily digest',
        'Your daily selection of new content is ready (in-app notice and email summary).', false, true),
       ('CONTENT_UPDATES', 'Content updates',
        'Relevant updates about stories you already received.', false, true)
ON CONFLICT (code) DO NOTHING;
