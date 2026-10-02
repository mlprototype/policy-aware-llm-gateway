ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS injection_score INTEGER;

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS injection_categories VARCHAR(500);
