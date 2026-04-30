package io.github.mlprototype.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GatewayMetrics {
    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordProviderRequestLatency(String provider, String status, long latencyMs) {
        Timer.builder("gateway.provider.requests")
                .tag("provider", provider)
                .tag("status", status)
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void incrementProviderFailure(String provider, String failureType) {
        registry.counter("gateway.provider.failures",
                "provider", provider,
                "failure_type", failureType).increment();
    }

    public void incrementFallbackSuccess(String primaryProvider, String fallbackProvider, String reason) {
        registry.counter("gateway.routing.fallbacks",
                "primary_provider", primaryProvider,
                "fallback_provider", fallbackProvider,
                "reason", reason).increment();
    }

    public void incrementSecurityBlock(String reason) {
        registry.counter("gateway.security.blocks", "reason", reason).increment();
    }

    public void incrementSecurityWarn(String reason) {
        registry.counter("gateway.security.warns", "reason", reason).increment();
    }

    public void incrementRateLimitReject() {
        registry.counter("gateway.ratelimit.rejects").increment();
    }

    public void incrementAuditPersistenceFailure() {
        registry.counter("gateway.audit.persistence.failures").increment();
    }
}
