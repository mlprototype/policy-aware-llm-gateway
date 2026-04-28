package io.github.mlprototype.gateway.audit;

import lombok.Builder;
import lombok.Data;

/**
 * Immutable audit event record for structured logging.
 */
@Data
@Builder
public class AuditEvent {

    private final String traceId;
    private final String provider;
    private final String model;
    private final long latencyMs;
    private final int statusCode;
    private final String status;  // "success" or "error"
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final String errorMessage;
}
