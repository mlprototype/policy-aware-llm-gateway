package io.github.mlprototype.gateway.exception;

/**
 * Base exception for all Gateway-level errors.
 */
public class GatewayException extends RuntimeException {

    private final int statusCode;

    public GatewayException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GatewayException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
