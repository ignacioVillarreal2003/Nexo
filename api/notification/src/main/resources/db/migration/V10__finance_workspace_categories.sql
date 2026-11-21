-- finance-workspace has been publishing notification commands under these
-- categories since NotificationCommandComposer was added, but none of them
-- were ever registered here — every one of those commands has been rejected
-- by NotificationCommandProcessor.validate() with "Unknown notification
-- category" since day one. This backfills the missing rows.
INSERT INTO notification_categories (code, name, description, is_mandatory, is_system)
VALUES ('FINANCE_WORKSPACE_BUDGET', 'Finance workspace - Budget',
        'Notifications when a budget crosses its alert threshold or is exceeded.', false, true),
       ('FINANCE_WORKSPACE_GOAL', 'Finance workspace - Goal',
        'Notifications when a savings goal is reached.', false, true),
       ('FINANCE_WORKSPACE_LIABILITY', 'Finance workspace - Liability',
        'Notifications when a debt is fully paid off.', false, true),
       ('FINANCE_WORKSPACE_ALERT', 'Finance workspace - Alert',
        'Low balances, cards near their limit, upcoming payments and goals falling behind pace.', false, true),
       ('FINANCE_WORKSPACE_BRIEFING', 'Finance workspace - Briefing',
        'Periodic summary of where your money stands.', false, true)
ON CONFLICT (code) DO NOTHING;
