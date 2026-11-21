-- Clientes de servicio para desarrollo local (perfil dev). En producción se crean desde
-- el panel de administración y el secreto se muestra una sola vez.
-- Secretos: nexo-collector-dev-secret, nexo-content-dev-secret, nexo-delivery-dev-secret.
INSERT INTO service_clients (id, name, description, secret_hash, scopes, active, created_by)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'nexo-collector', 'Collector: plan de recopilación e ingesta',
     '$2b$10$L4tWQALpYBYPtItBB0F7kOQgNpYdLpDjct835mtD4xazNLh.b1Uce',
     '["TOPIC_PLAN_READ", "SUBSCRIPTION_READ_UNRESTRICTED", "CONTENT_INGEST"]'::jsonb, TRUE, 'system'),
    ('c0000000-0000-0000-0000-000000000002', 'nexo-content', 'Content: análisis con el servicio de IA',
     '$2b$10$Skh0NHTpFipIB8Y1WAJFIe0gPQ0zROaGAT9/ISpjugTSvzfLiTE6.',
     '["AI_ANALYZE"]'::jsonb, TRUE, 'system'),
    ('c0000000-0000-0000-0000-000000000003', 'nexo-delivery', 'Delivery: suscripciones, tópicos y candidatos',
     '$2b$10$uC9tXQDac./60IVVA5LxfuXY6KFgJellqgleqOPsvPZyqUgfG5qVe',
     '["TOPIC_PLAN_READ", "SUBSCRIPTION_READ_UNRESTRICTED", "CONTENT_CANDIDATE_READ", "COLLECTION_REQUEST"]'::jsonb, TRUE, 'system')
ON CONFLICT (id) DO UPDATE SET scopes = EXCLUDED.scopes, secret_hash = EXCLUDED.secret_hash;
