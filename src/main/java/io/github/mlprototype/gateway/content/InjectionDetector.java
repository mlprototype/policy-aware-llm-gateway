package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects rule-based prompt injection patterns in chat messages.
 */
@Component
public class InjectionDetector {

    private final Map<String, Pattern> rules = new LinkedHashMap<>();

    public InjectionDetector() {
        rules.put("IGNORE_INSTRUCTIONS", Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|rules)"));
        rules.put("REVEAL_SYSTEM_PROMPT", Pattern.compile("(?i)(reveal|show|display|print|output)\\s+(the\\s+)?(system|hidden|internal)\\s+(prompt|instructions)"));
        rules.put("BYPASS_POLICY", Pattern.compile("(?i)(bypass|circumvent|ignore|disable)\\s+(the\\s+)?(policy|policies|safety|filter|guard)"));
        rules.put("ROLE_MANIPULATION", Pattern.compile("(?i)(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be)\\s+.{0,30}(without|with\\s+no)\\s+(any\\s+)?(restrict|filter|limit)"));
        rules.put("JAILBREAK_PATTERN", Pattern.compile("(?i)(DAN\\b|do\\s+anything\\s+now|jailbreak|developer\\s+mode)"));
    }

    /**
     * Detects prompt injection patterns in the given messages.
     *
     * @param messages the chat messages
     * @return the result containing detected matched rules
     */
    public InjectionDetectionResult detect(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new InjectionDetectionResult(false, List.of());
        }

        List<String> matchedRules = new ArrayList<>();

        for (Message message : messages) {
            String content = message.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, Pattern> entry : rules.entrySet()) {
                if (!matchedRules.contains(entry.getKey()) && entry.getValue().matcher(content).find()) {
                    matchedRules.add(entry.getKey());
                }
            }
        }

        return new InjectionDetectionResult(!matchedRules.isEmpty(), matchedRules);
    }
}
