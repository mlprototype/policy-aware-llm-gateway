package io.github.mlprototype.gateway.provider;

import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderFailureType;
import io.github.mlprototype.gateway.exception.Upstream4xxProviderException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Maps HTTP client and mapping failures to gateway failure taxonomy.
 */
@Component
public class ProviderFailureClassifier {

    public ProviderException upstream4xx(ProviderType providerType, int upstreamStatusCode) {
        return new Upstream4xxProviderException(
                providerType,
                upstreamStatusCode,
                providerType.getValue() + " client error: " + upstreamStatusCode);
    }

    public ProviderException upstream5xx(ProviderType providerType, int upstreamStatusCode) {
        return new ProviderException(
                providerType,
                ProviderFailureType.UPSTREAM_5XX,
                upstreamStatusCode,
                providerType.getValue() + " server error: " + upstreamStatusCode);
    }

    public ProviderException classifyClientException(ProviderType providerType, RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                return new ProviderException(
                        providerType,
                        ProviderFailureType.TIMEOUT,
                        null,
                        "Timed out calling " + providerType.getValue(),
                        exception);
            }
            if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
                return new ProviderException(
                        providerType,
                        ProviderFailureType.CONNECTION_ERROR,
                        null,
                        "Connection error calling " + providerType.getValue(),
                        exception);
            }
            return new ProviderException(
                    providerType,
                    ProviderFailureType.CONNECTION_ERROR,
                    null,
                    "Transport error calling " + providerType.getValue(),
                    exception);
        }

        return invalidResponse(providerType, "Invalid response from " + providerType.getValue(), exception);
    }

    public ProviderException invalidResponse(ProviderType providerType, String message, Throwable cause) {
        return new ProviderException(
                providerType,
                ProviderFailureType.INVALID_RESPONSE,
                null,
                message,
                cause);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
