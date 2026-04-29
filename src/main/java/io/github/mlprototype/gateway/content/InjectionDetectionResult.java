package io.github.mlprototype.gateway.content;

import java.util.List;

/**
 * Result of prompt injection detection.
 */
public record InjectionDetectionResult(
        boolean detected,
        List<String> matchedRules
) {
}
