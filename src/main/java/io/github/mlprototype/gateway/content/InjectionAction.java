package io.github.mlprototype.gateway.content;

/**
 * Action to take when a prompt injection is detected in a request.
 */
public enum InjectionAction {
    ALLOW,
    WARN,
    BLOCK
}
