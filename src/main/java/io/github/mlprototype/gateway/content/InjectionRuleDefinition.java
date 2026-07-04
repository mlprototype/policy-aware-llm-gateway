package io.github.mlprototype.gateway.content;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Definition of a scored prompt injection rule.
 */
public record InjectionRuleDefinition(
        String id,
        InjectionCategory category,
        int score,
        List<Pattern> patterns
) {
    public InjectionRuleDefinition {
        patterns = List.copyOf(patterns);
    }
}
