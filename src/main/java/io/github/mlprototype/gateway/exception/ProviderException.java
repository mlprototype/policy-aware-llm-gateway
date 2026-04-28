package io.github.mlprototype.gateway.exception;

import io.github.mlprototype.gateway.provider.ProviderType;

/**
 * Exception thrown when an LLM provider call fails.
 * Carries the provider type for error reporting and routing decisions.
 */
public class ProviderException extends GatewayException {

    private final ProviderType providerType;

    public ProviderException(ProviderType providerType, String message, int statusCode) {
        super(message, statusCode);
        this.providerType = providerType;
    }

    public ProviderException(ProviderType providerType, String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
        this.providerType = providerType;
    }

    public ProviderType getProviderType() {
        return providerType;
    }
}
