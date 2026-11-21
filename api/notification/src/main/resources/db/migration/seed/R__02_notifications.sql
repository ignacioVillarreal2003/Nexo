INSERT INTO notifications
    (id, recipient_id, created_by, category, type, severity, priority, group_key, target_type, target_id,
     title, body, data, read_at, created_at)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-00000000000f', NULL,
     'AUTHENTICATION_SECURITY', 'EMAIL_VERIFIED', 'SUCCESS', 0, NULL, NULL, NULL,
     'Email verified', 'Your email address has been verified.', NULL,
     NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    ('c0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-00000000000f', NULL,
     'SYSTEM', 'SYSTEM_ANNOUNCEMENT', 'INFO', 0, NULL, NULL, NULL,
     'Welcome to Nexo', 'Follow a few topics in Explore and your first digest will arrive tomorrow.', NULL,
     NULL, NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
