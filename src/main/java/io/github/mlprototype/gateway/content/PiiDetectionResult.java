package io.github.mlprototype.gateway.content;

import java.util.List;

/**
 * Result of PII detection.
 */
public record PiiDetectionResult(
        boolean detected,
        List<PiiPatternType> matchedPatterns
) {
}
