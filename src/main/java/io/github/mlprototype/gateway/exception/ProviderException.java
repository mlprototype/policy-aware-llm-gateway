package io.github.mlprototype.gateway.exception;

import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.Getter;

/**
 * Exception thrown when an LLM provider call fails.
 * Carries the provider type for error reporting and routing decisions.
 */
@Getter
public class ProviderException extends GatewayException {

    private final ProviderType providerType;
    private final ProviderFailureType failureType;
    private final Integer upstreamStatusCode;

    public ProviderException(
            ProviderType providerType,
            ProviderFailureType failureType,
            Integer upstreamStatusCode,
            String message) {
        super(message, failureType.getGatewayStatus());
        this.providerType = providerType;
        this.failureType = failureType;
        this.upstreamStatusCode = upstreamStatusCode;
    }

    public ProviderException(
            ProviderType providerType,
            ProviderFailureType failureType,
            Integer upstreamStatusCode,
            String message,
            Throwable cause) {
        super(message, failureType.getGatewayStatus(), cause);
        this.providerType = providerType;
        this.failureType = failureType;
        this.upstreamStatusCode = upstreamStatusCode;
    }

    public boolean isFallbackEligible() {
        return failureType.isFallbackEligible();
    }
}
