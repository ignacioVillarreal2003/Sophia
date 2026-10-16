INSERT INTO comments
    (id, workspace_id, artifact_id, block_id, parent_comment_id, author_id, body, mentions, reactions,
     edited, created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000301', 'b0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', NULL,
     'a0000000-0000-0000-0000-000000000010', 'Looks great, thanks!', '[]'::jsonb, '{}'::jsonb,
     FALSE, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000302', 'b0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000013', NULL, NULL,
     'a0000000-0000-0000-0000-000000000010', 'Nice progress — can you review the Q2 section?',
     '["a0000000-0000-0000-0000-00000000000f"]'::jsonb, '{}'::jsonb,
     FALSE, NOW() - INTERVAL '18 hours', NOW() - INTERVAL '18 hours')
ON CONFLICT (id) DO NOTHING;
