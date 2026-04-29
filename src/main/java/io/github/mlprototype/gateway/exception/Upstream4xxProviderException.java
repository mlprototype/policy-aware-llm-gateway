package io.github.mlprototype.gateway.exception;

import io.github.mlprototype.gateway.provider.ProviderType;

/**
 * Dedicated subtype so circuit breaker can ignore upstream 4xx responses.
 */
public class Upstream4xxProviderException extends ProviderException {

    public Upstream4xxProviderException(
            ProviderType providerType,
            Integer upstreamStatusCode,
            String message) {
        super(providerType, ProviderFailureType.UPSTREAM_4XX, upstreamStatusCode, message);
    }
}
