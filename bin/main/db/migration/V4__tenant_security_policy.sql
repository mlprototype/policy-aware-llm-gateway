-- Sprint 4: Tenant security policy
-- Add configuration columns for PII and Prompt Injection actions

ALTER TABLE tenants
    ADD COLUMN pii_action VARCHAR(10),
    ADD COLUMN injection_action VARCHAR(10);

-- Existing tenants will have NULL, which defaults to application.yml settings
