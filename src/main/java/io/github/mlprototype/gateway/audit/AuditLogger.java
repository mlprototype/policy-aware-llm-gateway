package io.github.mlprototype.gateway.audit;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.stereotype.Component;

/**
 * Audit logger that outputs structured JSON log entries.
 * Sprint 1: SLF4J structured logging only.
 * Sprint 4 extension: DB persistence via AuditRepository.
 */
@Slf4j
@Component
public class AuditLogger {

    /**
     * Logs an audit event as structured JSON.
     * Fields: trace_id, provider, model, latency_ms, status, tokens.
     */
    public void log(AuditEvent event) {
        if ("error".equals(event.getStatus())) {
            log.warn("audit_event {}",
                    StructuredArguments.entries(toMap(event)));
        } else {
            log.info("audit_event {}",
                    StructuredArguments.entries(toMap(event)));
        }
    }

    private java.util.Map<String, Object> toMap(AuditEvent event) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("trace_id", event.getTraceId());
        map.put("provider", event.getProvider());
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
        return map;
    }
}
