package io.github.mlprototype.gateway.exception;

/**
 * Failure taxonomy for upstream provider calls.
 * Determines fallback eligibility, circuit-breaker accounting, and
 * client-facing HTTP status normalization.
 */
public enum ProviderFailureType {

    TIMEOUT(true, true, 503),
    CONNECTION_ERROR(true, true, 503),
    UPSTREAM_5XX(true, true, 502),
    BREAKER_OPEN(true, false, 503),
    UPSTREAM_4XX(false, false, 502),
    INVALID_RESPONSE(false, true, 502);

    private final boolean fallbackEligible;
    private final boolean circuitBreakerFailure;
    private final int gatewayStatus;

    ProviderFailureType(boolean fallbackEligible, boolean circuitBreakerFailure, int gatewayStatus) {
        this.fallbackEligible = fallbackEligible;
        this.circuitBreakerFailure = circuitBreakerFailure;
        this.gatewayStatus = gatewayStatus;
    }

    public boolean isFallbackEligible() {
        return fallbackEligible;
    }

    public boolean isCircuitBreakerFailure() {
        return circuitBreakerFailure;
    }

    public int getGatewayStatus() {
        return gatewayStatus;
    }
}
