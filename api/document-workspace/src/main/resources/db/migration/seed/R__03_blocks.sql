INSERT INTO blocks (id, document_id, workspace_id, parent_block_id, type, content, sort_key, created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000101', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'HEADING',
     '{"text":"Welcome to Sophia Docs","level":1}'::jsonb, 'V', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000102', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'PARAGRAPH',
     '{"text":"This is your workspace for notes, specs, and everyday docs. Organize pages into folders, tag them, and share with teammates."}'::jsonb,
     'k', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000103', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Invite your team","checked":false}'::jsonb, 's', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000104', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Create your first folder","checked":true}'::jsonb, 'w', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000105', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Pin your favorite pages","checked":false}'::jsonb, 'y', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000106', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'DIVIDER', '{}'::jsonb, 'z', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000107', 'b0000000-0000-0000-0000-000000000012',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CALLOUT',
     '{"text":"Tip: press \"/\" inside a document to insert a new block."}'::jsonb, 'zV', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO blocks (id, document_id, workspace_id, parent_block_id, type, content, sort_key, created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000111', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'HEADING',
     '{"text":"Product Roadmap","level":1}'::jsonb, 'V', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000112', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'PARAGRAPH',
     '{"text":"High-level plan for the next few releases."}'::jsonb, 'k', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000113', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'HEADING',
     '{"text":"Q1","level":2}'::jsonb, 's', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000114', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Ship notifications center","checked":true}'::jsonb, 'w', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000115', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Finance workspace dashboard","checked":true}'::jsonb, 'y', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000116', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'HEADING',
     '{"text":"Q2","level":2}'::jsonb, 'z', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000117', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Real-time collaboration","checked":false}'::jsonb, 'zV', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000118', 'b0000000-0000-0000-0000-000000000013',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Mobile app beta","checked":false}'::jsonb, 'zk', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO blocks (id, document_id, workspace_id, parent_block_id, type, content, sort_key, created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000121', 'b0000000-0000-0000-0000-000000000014',
     'b0000000-0000-0000-0000-000000000001', NULL, 'HEADING',
     '{"text":"Weekly Sync — Notes","level":1}'::jsonb, 'V', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000122', 'b0000000-0000-0000-0000-000000000014',
     'b0000000-0000-0000-0000-000000000001', NULL, 'PARAGRAPH',
     '{"text":"Attendees: Demo Owner"}'::jsonb, 'k', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000123', 'b0000000-0000-0000-0000-000000000014',
     'b0000000-0000-0000-0000-000000000001', NULL, 'PARAGRAPH',
     '{"text":"Discussed onboarding flow and upcoming releases."}'::jsonb, 's', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000124', 'b0000000-0000-0000-0000-000000000014',
     'b0000000-0000-0000-0000-000000000001', NULL, 'CHECKLIST_ITEM',
     '{"text":"Follow up on onboarding copy","checked":false}'::jsonb, 'w', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
