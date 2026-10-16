INSERT INTO workspaces
    (id, name, description, logo_url, color, domain, language, owner_id, status, settings,
     created_at, updated_at, lock_version)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'Demo Workspace',
     'Sample workspace with a few documents to explore.', NULL, '#6366F1', NULL, 'en',
     'a0000000-0000-0000-0000-00000000000f', 'ACTIVE',
     '{"theme":"SYSTEM","timezone":"America/Montevideo","dateFormat":"yyyy-MM-dd","brandColor":"#6366F1","defaultSecurityLevel":"INTERNAL","trashRetentionDays":null,"storageQuotaMb":null,"publicSharingEnabled":false}'::jsonb,
     NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO workspace_members
    (id, workspace_id, user_id, email, display_name, role, clearance, status, invited_by,
     joined_at, created_at, updated_at, lock_version)
VALUES
    ('b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-00000000000f', 'demo@sophia.local', 'Demo Owner', 'OWNER', 'INTERNAL',
     'ACTIVE', NULL, NOW(), NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;
