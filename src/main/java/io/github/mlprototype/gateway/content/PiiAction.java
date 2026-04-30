package io.github.mlprototype.gateway.content;

/**
 * Action to take when PII is detected in a request.
 */
public enum PiiAction {
    ALLOW,
    MASK,
    BLOCK
}
