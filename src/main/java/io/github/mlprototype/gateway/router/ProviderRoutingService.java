package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.config.ProviderRoutingProperties;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.observability.GatewayMetrics;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.resilience.CircuitBreakerProviderInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves requested provider, executes primary provider, and performs
 * single-step fallback when eligible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderRoutingService {

    private final ProviderRegistry providerRegistry;
    private final ProviderRoutingProperties routingProperties;
    private final FallbackPolicy fallbackPolicy;
    private final CircuitBreakerProviderInvoker circuitBreakerProviderInvoker;
    private final GatewayMetrics gatewayMetrics;

    public ProviderExecutionResult execute(
            ChatRequest request,
            String requestedProviderHeader,
            String legacyProviderHeader) {
        ProviderType requestedProvider = resolveRequestedProvider(requestedProviderHeader, legacyProviderHeader);
        LlmProvider primaryProvider = providerRegistry.find(requestedProvider)
                .orElseThrow(() -> ProviderRoutingException.serviceUnavailable(
                        "Provider not registered: " + requestedProvider.getValue(),
                        requestedProvider,
                        requestedProvider,
                        false,
                        null,
                        null,
                        null));

        try {
            ChatResponse response = circuitBreakerProviderInvoker.invoke(primaryProvider, request);
            return ProviderExecutionResult.builder()
                    .requestedProvider(requestedProvider)
                    .resolvedProvider(primaryProvider.getType())
                    .fallbackUsed(false)
                    .fallbackReason(null)
                    .response(response)
                    .build();
        } catch (ProviderException primaryFailure) {
            if (!primaryFailure.isFallbackEligible()) {
                throw ProviderRoutingException.fromPrimaryFailure(
                        requestedProvider,
                        primaryProvider.getType(),
                        primaryFailure);
            }

            return executeFallback(request, requestedProvider, primaryProvider, primaryFailure);
        }
    }

    private ProviderExecutionResult executeFallback(
            ChatRequest request,
            ProviderType requestedProvider,
            LlmProvider primaryProvider,
            ProviderException primaryFailure) {
        FallbackReason fallbackReason = FallbackReason.fromFailureType(primaryFailure.getFailureType());
        ProviderType fallbackType = fallbackPolicy.fallbackFor(primaryProvider.getType())
                .orElseThrow(() -> ProviderRoutingException.fromPrimaryFailure(
                        requestedProvider,
                        primaryProvider.getType(),
                        primaryFailure));

        LlmProvider fallbackProvider = providerRegistry.find(fallbackType)
                .orElseThrow(() -> ProviderRoutingException.serviceUnavailable(
                        "Fallback provider not registered: " + fallbackType.getValue(),
                        requestedProvider,
                        primaryProvider.getType(),
                        false,
                        fallbackReason,
                        primaryFailure.getFailureType(),
                        primaryFailure.getFailureType()));

        log.warn("provider_fallback {}",
                StructuredArguments.entries(structuredFields(
                        requestedProvider.getValue(),
                        fallbackType.getValue(),
                        true,
                        fallbackReason.name(),
                        primaryFailure.getFailureType().name(),
                        null)));

        try {
            ChatResponse response = circuitBreakerProviderInvoker.invoke(fallbackProvider, request);
            gatewayMetrics.incrementFallbackSuccess(
                    primaryProvider.getType().getValue(),
                    fallbackProvider.getType().getValue(),
                    fallbackReason.name());
            return ProviderExecutionResult.builder()
                    .requestedProvider(requestedProvider)
                    .resolvedProvider(fallbackProvider.getType())
                    .fallbackUsed(true)
                    .fallbackReason(fallbackReason)
                    .response(response)
                    .build();
        } catch (ProviderException fallbackFailure) {
            log.warn("provider_fallback_failed {}",
                    StructuredArguments.entries(structuredFields(
                            requestedProvider.getValue(),
                            fallbackProvider.getType().getValue(),
                            true,
                            fallbackReason.name(),
                            primaryFailure.getFailureType().name(),
                            fallbackFailure.getFailureType().name())));

            throw ProviderRoutingException.fromFinalFailure(
                    requestedProvider,
                    fallbackProvider.getType(),
                    fallbackReason,
                    primaryFailure.getFailureType(),
                    fallbackFailure);
        }
    }

    private ProviderType resolveRequestedProvider(String requestedProviderHeader, String legacyProviderHeader) {
        String requested = normalize(requestedProviderHeader);
        String legacy = normalize(legacyProviderHeader);

        if (requested != null && legacy != null && !requested.equalsIgnoreCase(legacy)) {
            throw ProviderRoutingException.badRequest(
                    "Conflicting provider headers: " + requested + " vs " + legacy);
        }

        if (legacy != null) {
            log.warn("deprecated provider request header used: header={}, value={}",
                    io.github.mlprototype.gateway.api.GatewayHeaders.PROVIDER_HEADER,
                    legacy);
        }

        String effective = requested != null ? requested : legacy;
        if (effective == null) {
            return routingProperties.getDefaultProviderType();
        }

        try {
            return ProviderType.fromValue(effective);
        } catch (IllegalArgumentException exception) {
            throw ProviderRoutingException.badRequest(exception.getMessage());
        }
    }

    private String normalize(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return headerValue.trim();
    }

    private Map<String, Object> structuredFields(
            String requestedProvider,
            String resolvedProvider,
            boolean fallbackUsed,
            String fallbackReason,
            String primaryFailureType,
            String finalFailureType) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requested_provider", requestedProvider);
        fields.put("resolved_provider", resolvedProvider);
        fields.put("fallback_used", fallbackUsed);
        fields.put("fallback_reason", fallbackReason);
        fields.put("primary_failure_type", primaryFailureType);
        if (finalFailureType != null) {
            fields.put("final_failure_type", finalFailureType);
        }
        return fields;
    }
}

