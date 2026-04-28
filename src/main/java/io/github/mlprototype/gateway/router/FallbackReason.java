package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.exception.ProviderFailureType;

/**
 * Stable fallback reasons for logs, audit fields, and tests.
 */
public enum FallbackReason {

    PRIMARY_TIMEOUT,
    PRIMARY_CONNECTION_ERROR,
    PRIMARY_UPSTREAM_5XX,
    PRIMARY_BREAKER_OPEN;

    public static FallbackReason fromFailureType(ProviderFailureType failureType) {
        return switch (failureType) {
            case TIMEOUT -> PRIMARY_TIMEOUT;
            case CONNECTION_ERROR -> PRIMARY_CONNECTION_ERROR;
            case UPSTREAM_5XX -> PRIMARY_UPSTREAM_5XX;
            case BREAKER_OPEN -> PRIMARY_BREAKER_OPEN;
            default -> throw new IllegalArgumentException("Unsupported fallback failure type: " + failureType);
        };
    }
}
