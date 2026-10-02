-- Dev seed data for local/test environments
-- API Key: dev-gateway-key-001
-- SHA-256: 6663b417b7fac3ec32d11e6ab0f88fdb7305fe1157f08fed45e67451b5232b49

INSERT INTO tenants (id, name, status, rate_limit)
VALUES ('11111111-1111-1111-1111-111111111111', 'dev-tenant', 'ACTIVE', 60)
ON CONFLICT (id) DO NOTHING;

INSERT INTO api_clients (id, tenant_id, name, api_key_hash, status)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'dev-client',
    '6663b417b7fac3ec32d11e6ab0f88fdb7305fe1157f08fed45e67451b5232b49',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

-- Suspended tenant for testing 403
INSERT INTO tenants (id, name, status, rate_limit)
VALUES ('33333333-3333-3333-3333-333333333333', 'suspended-tenant', 'SUSPENDED', 10)
ON CONFLICT (id) DO NOTHING;

-- API Key: suspended-key-001
-- SHA-256 of 'suspended-key-001'
INSERT INTO api_clients (id, tenant_id, name, api_key_hash, status)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '33333333-3333-3333-3333-333333333333',
    'suspended-client',
    '5a1ea5ad2e1e1dbe73b6bee424b8337aea880eb6e5e8c8b281fdbfe843530b92',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;
