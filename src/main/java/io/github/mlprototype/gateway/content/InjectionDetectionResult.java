package io.github.mlprototype.gateway.content;

import java.util.List;

/**
 * Result of prompt injection detection.
 */
public record InjectionDetectionResult(
        boolean detected,
        int score,
        List<InjectionCategory> categories,
        List<String> matchedRules,
        List<InjectionRuleMatch> matches
) {
    public InjectionDetectionResult {
        categories = List.copyOf(categories);
        matchedRules = List.copyOf(matchedRules);
        matches = List.copyOf(matches);
    }

    public static InjectionDetectionResult none() {
        return new InjectionDetectionResult(false, 0, List.of(), List.of(), List.of());
    }
}
