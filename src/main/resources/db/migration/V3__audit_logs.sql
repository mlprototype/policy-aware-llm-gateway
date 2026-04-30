-- Sprint 4: Audit Log Persistence
-- Table to persist audit events including security decisions

CREATE TABLE audit_logs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id           VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    client_id          VARCHAR(64) NOT NULL,
    requested_provider VARCHAR(20),
    resolved_provider  VARCHAR(20),
    model              VARCHAR(100),
    status_code        INT NOT NULL,
    status             VARCHAR(20) NOT NULL,  -- success / error / blocked
    latency_ms         BIGINT,
    fallback_used      BOOLEAN DEFAULT FALSE,
    fallback_reason    VARCHAR(40),
    prompt_tokens      INT,
    completion_tokens  INT,
    total_tokens       INT,
    pii_detected       BOOLEAN DEFAULT FALSE,
    pii_action         VARCHAR(10),
    pii_patterns       VARCHAR(500),
    injection_detected BOOLEAN DEFAULT FALSE,
    injection_action   VARCHAR(10),
    injection_rules    VARCHAR(500),
    error_message      VARCHAR(1000),
    request_hash       VARCHAR(64),           -- raw request 由来の deterministic hash (相関用)
    request_preview    VARCHAR(200),          -- mask 適用後の短縮文字列
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant ON audit_logs(tenant_id, created_at);
CREATE INDEX idx_audit_logs_trace ON audit_logs(trace_id);
CREATE INDEX idx_audit_logs_status ON audit_logs(status, created_at);
CREATE INDEX idx_audit_logs_pii ON audit_logs(pii_detected, created_at);
CREATE INDEX idx_audit_logs_injection ON audit_logs(injection_detected, created_at);
