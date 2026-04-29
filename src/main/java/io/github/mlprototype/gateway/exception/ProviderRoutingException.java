package io.github.mlprototype.gateway.exception;

import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.router.FallbackReason;
import lombok.Getter;

/**
 * Client-facing routing failure after primary/fallback resolution is complete.
 */
@Getter
public class ProviderRoutingException extends GatewayException {

    private final ProviderType requestedProvider;
    private final ProviderType resolvedProvider;
    private final boolean fallbackUsed;
    private final FallbackReason fallbackReason;
    private final ProviderFailureType primaryFailureType;
    private final ProviderFailureType finalFailureType;

    public ProviderRoutingException(
            String message,
            int statusCode,
            ProviderType requestedProvider,
            ProviderType resolvedProvider,
            boolean fallbackUsed,
            FallbackReason fallbackReason,
            ProviderFailureType primaryFailureType,
            ProviderFailureType finalFailureType,
            Throwable cause) {
        super(message, statusCode, cause);
        this.requestedProvider = requestedProvider;
        this.resolvedProvider = resolvedProvider;
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = fallbackReason;
        this.primaryFailureType = primaryFailureType;
        this.finalFailureType = finalFailureType;
    }

    public static ProviderRoutingException fromPrimaryFailure(
            ProviderType requestedProvider,
            ProviderType resolvedProvider,
            ProviderException cause) {
        return new ProviderRoutingException(
                cause.getMessage(),
                cause.getStatusCode(),
                requestedProvider,
                resolvedProvider,
                false,
                null,
                cause.getFailureType(),
                cause.getFailureType(),
                cause);
    }

    public static ProviderRoutingException fromFinalFailure(
            ProviderType requestedProvider,
            ProviderType resolvedProvider,
            FallbackReason fallbackReason,
            ProviderFailureType primaryFailureType,
            ProviderException cause) {
        return new ProviderRoutingException(
                cause.getMessage(),
                cause.getStatusCode(),
                requestedProvider,
                resolvedProvider,
                true,
                fallbackReason,
                primaryFailureType,
                cause.getFailureType(),
                cause);
    }

    public static ProviderRoutingException badRequest(String message) {
        return new ProviderRoutingException(message, 400, null, null, false, null, null, null, null);
    }

    public static ProviderRoutingException serviceUnavailable(
            String message,
            ProviderType requestedProvider,
            ProviderType resolvedProvider,
            boolean fallbackUsed,
            FallbackReason fallbackReason,
            ProviderFailureType primaryFailureType,
            ProviderFailureType finalFailureType) {
        return new ProviderRoutingException(
                message,
                503,
                requestedProvider,
                resolvedProvider,
                fallbackUsed,
                fallbackReason,
                primaryFailureType,
                finalFailureType,
                null);
    }
}
