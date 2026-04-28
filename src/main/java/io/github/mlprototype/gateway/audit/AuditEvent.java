package io.github.mlprototype.gateway.audit;

import lombok.Builder;
import lombok.Data;

/**
 * Audit event record for structured logging.
 * Sprint 2: log-output-only internal event model.
 * DB persistence will use a separate AuditLogEntity in Sprint 4.
 */
@Data
@Builder
public class AuditEvent {

    private final String traceId;
    private final String tenantId;
    private final String clientId;
    private final String provider;
    private final String model;
    private final long latencyMs;
    private final int statusCode;
    private final String status;  // "success" or "error"
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final String errorMessage;
    private final String rateLimitResult;  // "allowed" / "rejected" / "redis_unavailable_fail_open"
}
