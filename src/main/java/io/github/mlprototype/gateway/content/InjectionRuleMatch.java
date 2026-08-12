package io.github.mlprototype.gateway.content;

/**
 * Metadata for a prompt injection rule matched within a request.
 */
public record InjectionRuleMatch(
        String ruleId,
        InjectionCategory category,
        int score,
        boolean detectOnMatch
) {
}
