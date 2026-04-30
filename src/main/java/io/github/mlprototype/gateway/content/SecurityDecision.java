package io.github.mlprototype.gateway.content;

/**
 * Represents the security decision after evaluating a request against PII and Injection detectors.
 */
public record SecurityDecision(
        PiiDetectionResult piiResult,
        PiiAction piiAction,
        InjectionDetectionResult injectionResult,
        InjectionAction injectionAction
) {
}
