package io.github.mlprototype.gateway.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an audit log entry stored in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "requested_provider", length = 20)
    private String requestedProvider;

    @Column(name = "resolved_provider", length = 20)
    private String resolvedProvider;

    @Column(length = 100)
    private String model;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "fallback_used")
    @Builder.Default
    private Boolean fallbackUsed = false;

    @Column(name = "fallback_reason", length = 40)
    private String fallbackReason;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "pii_detected")
    @Builder.Default
    private Boolean piiDetected = false;

    @Column(name = "pii_action", length = 10)
    private String piiAction;

    @Column(name = "pii_patterns", length = 500)
    private String piiPatterns;

    @Column(name = "injection_detected")
    @Builder.Default
    private Boolean injectionDetected = false;

    @Column(name = "injection_action", length = 10)
    private String injectionAction;

    @Column(name = "injection_rules", length = 500)
    private String injectionRules;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "request_preview", length = 200)
    private String requestPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
