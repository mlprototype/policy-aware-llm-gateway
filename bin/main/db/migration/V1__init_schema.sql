-- Initial schema for LLM Gateway
-- Sprint 1: minimal schema for Flyway validation
-- Sprint 4: audit_logs table will be added here

CREATE TABLE IF NOT EXISTS gateway_metadata (
    key   VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed with schema version marker
INSERT INTO gateway_metadata (key, value)
VALUES ('schema_version', '1')
ON CONFLICT (key) DO NOTHING;
