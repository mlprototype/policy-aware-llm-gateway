package io.github.mlprototype.gateway.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.stereotype.Component;

/**
 * Audit logger that outputs structured JSON log entries and persists to the database.
 * Sprint 4: Dual-write (SLF4J + DB) with fail-open for DB insert.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    /**
     * Logs an audit event as structured JSON and saves it to the database.
     */
    public void log(AuditEvent event) {
        // 1. Structured JSON log (original behavior)
        if ("error".equals(event.getStatus()) || "blocked".equals(event.getStatus())) {
            log.warn("audit_event {}",
                    StructuredArguments.entries(toMap(event)));
        } else {
            log.info("audit_event {}",
                    StructuredArguments.entries(toMap(event)));
        }

        // 2. DB Persistence
        try {
            AuditLogEntity entity = AuditLogEntity.builder()
                    .traceId(event.getTraceId())
                    .tenantId(event.getTenantId())
                    .clientId(event.getClientId())
                    .requestedProvider(event.getRequestedProvider() != null ? event.getRequestedProvider() : event.getProvider())
                    .resolvedProvider(event.getResolvedProvider() != null ? event.getResolvedProvider() : event.getProvider())
                    .model(event.getModel())
                    .statusCode(event.getStatusCode())
                    .status(event.getStatus())
                    .latencyMs(event.getLatencyMs())
                    .fallbackUsed(event.getFallbackUsed() != null ? event.getFallbackUsed() : false)
                    .fallbackReason(event.getFallbackReason())
                    .promptTokens(event.getPromptTokens())
                    .completionTokens(event.getCompletionTokens())
                    .totalTokens(event.getTotalTokens())
                    .errorMessage(event.getErrorMessage())
                    .piiDetected(event.getPiiDetected() != null ? event.getPiiDetected() : false)
                    .piiAction(event.getPiiAction())
                    .piiPatterns(event.getPiiPatterns())
                    .injectionDetected(event.getInjectionDetected() != null ? event.getInjectionDetected() : false)
                    .injectionAction(event.getInjectionAction())
                    .injectionRules(event.getInjectionRules())
                    .requestHash(event.getRequestHash())
                    .requestPreview(event.getRequestPreview())
                    .build();
            auditLogRepository.save(entity);
        } catch (Exception e) {
            // Fail-open: log the error but don't disrupt the main flow
            log.error("Failed to persist audit log to DB for trace_id={}: {}", event.getTraceId(), e.getMessage(), e);
        }
    }

    private java.util.Map<String, Object> toMap(AuditEvent event) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("trace_id", event.getTraceId());
        map.put("tenant_id", event.getTenantId());
        map.put("client_id", event.getClientId());
        map.put("provider", event.getProvider());
        if (event.getRequestedProvider() != null) {
            map.put("requested_provider", event.getRequestedProvider());
        }
        if (event.getResolvedProvider() != null) {
            map.put("resolved_provider", event.getResolvedProvider());
        }
        if (event.getFallbackUsed() != null) {
            map.put("fallback_used", event.getFallbackUsed());
        }
        if (event.getFallbackReason() != null) {
            map.put("fallback_reason", event.getFallbackReason());
        }
        map.put("model", event.getModel());
        map.put("latency_ms", event.getLatencyMs());
        map.put("status_code", event.getStatusCode());
        map.put("status", event.getStatus());
        if (event.getPromptTokens() != null) {
            map.put("prompt_tokens", event.getPromptTokens());
            map.put("completion_tokens", event.getCompletionTokens());
            map.put("total_tokens", event.getTotalTokens());
        }
        if (event.getErrorMessage() != null) {
            map.put("error_message", event.getErrorMessage());
        }
        if (event.getRateLimitResult() != null) {
            map.put("rate_limit_result", event.getRateLimitResult());
        }
        
        // Security fields
        if (event.getPiiDetected() != null) {
            map.put("pii_detected", event.getPiiDetected());
            if (event.getPiiAction() != null) map.put("pii_action", event.getPiiAction());
            if (event.getPiiPatterns() != null) map.put("pii_patterns", event.getPiiPatterns());
        }
        if (event.getInjectionDetected() != null) {
            map.put("injection_detected", event.getInjectionDetected());
            if (event.getInjectionAction() != null) map.put("injection_action", event.getInjectionAction());
            if (event.getInjectionRules() != null) map.put("injection_rules", event.getInjectionRules());
        }
        if (event.getRequestHash() != null) map.put("request_hash", event.getRequestHash());
        if (event.getRequestPreview() != null) map.put("request_preview", event.getRequestPreview());
        
        return map;
    }
}
