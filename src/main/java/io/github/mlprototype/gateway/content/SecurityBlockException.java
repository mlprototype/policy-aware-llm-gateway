package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.exception.GatewayException;
import lombok.Getter;

/**
 * Exception thrown when a request is blocked by a security policy (e.g. PII or Injection).
 */
@Getter
public class SecurityBlockException extends GatewayException {

    private final String blockReason;
    private final SecurityDecision decision;
    private final int statusCode;
    private final String sanitizedPreview;
    private final String requestHash;

    public SecurityBlockException(String blockReason, SecurityDecision decision, int statusCode, String sanitizedPreview, String requestHash) {
        super("Request blocked due to security policy: " + blockReason, statusCode);
        this.blockReason = blockReason;
        this.decision = decision;
        this.statusCode = statusCode;
        this.sanitizedPreview = sanitizedPreview;
        this.requestHash = requestHash;
    }
}
