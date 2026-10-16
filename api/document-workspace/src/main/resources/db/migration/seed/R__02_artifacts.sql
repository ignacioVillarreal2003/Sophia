INSERT INTO artifacts
    (id, kind, artifact_type, workspace_id, parent_id, path, name, owner_id, security_label,
     metadata, position, created_at, updated_at, lock_version, icon, content_version)
VALUES
    ('b0000000-0000-0000-0000-000000000011', 'FOLDER', 'FOLDER', 'b0000000-0000-0000-0000-000000000001',
     NULL, '', 'Product', 'a0000000-0000-0000-0000-00000000000f', 'INTERNAL', NULL, 0,
     NOW(), NOW(), 0, 'folder', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO artifacts
    (id, kind, artifact_type, workspace_id, parent_id, path, name, owner_id, security_label,
     metadata, position, created_at, updated_at, lock_version, icon, content_version)
VALUES
    ('b0000000-0000-0000-0000-000000000012', 'DOCUMENT', 'RICH_TEXT', 'b0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000011.',
     'Getting Started', 'a0000000-0000-0000-0000-00000000000f', 'INTERNAL', NULL, 0,
     NOW(), NOW(), 0, 'rocket', 0),
    ('b0000000-0000-0000-0000-000000000013', 'DOCUMENT', 'RICH_TEXT', 'b0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000011.',
     'Roadmap', 'a0000000-0000-0000-0000-00000000000f', 'INTERNAL', NULL, 1,
     NOW(), NOW(), 0, 'map', 0),
    ('b0000000-0000-0000-0000-000000000014', 'DOCUMENT', 'RICH_TEXT', 'b0000000-0000-0000-0000-000000000001',
     NULL, '', 'Meeting Notes', 'a0000000-0000-0000-0000-00000000000f', 'INTERNAL', NULL, 1,
     NOW(), NOW(), 0, 'notebook', 0),
    ('b0000000-0000-0000-0000-000000000015', 'TABLE', 'TABLE', 'b0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000011.',
     'Task Tracker', 'a0000000-0000-0000-0000-00000000000f', 'INTERNAL', NULL, 2,
     NOW(), NOW(), 0, 'table', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tags (id, workspace_id, name, color, parent_tag_id, created_at, updated_at, lock_version)
VALUES
    ('b0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000001',
     'Guides', '#0EA5E9', NULL, NOW(), NOW(), 0),
    ('b0000000-0000-0000-0000-000000000022', 'b0000000-0000-0000-0000-000000000001',
     'Planning', '#F97316', NULL, NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tag_artifacts (id, tag_id, artifact_id, assigned_by, created_at, updated_at, lock_version)
VALUES
    ('b0000000-0000-0000-0000-000000000031', 'b0000000-0000-0000-0000-000000000021',
     'b0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-00000000000f', NOW(), NOW(), 0),
    ('b0000000-0000-0000-0000-000000000032', 'b0000000-0000-0000-0000-000000000022',
     'b0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-00000000000f', NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO favorites (id, workspace_id, user_id, artifact_id, pinned, created_at, updated_at, lock_version)
VALUES
    ('b0000000-0000-0000-0000-000000000041', 'b0000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-00000000000f', 'b0000000-0000-0000-0000-000000000012',
     TRUE, NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;
