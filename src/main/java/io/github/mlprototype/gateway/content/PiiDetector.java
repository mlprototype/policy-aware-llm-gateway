package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects Personally Identifiable Information (PII) in chat messages.
 */
@Component
public class PiiDetector {

    private final Map<PiiPatternType, Pattern> patterns = new EnumMap<>(PiiPatternType.class);

    public PiiDetector() {
        patterns.put(PiiPatternType.EMAIL, Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        patterns.put(PiiPatternType.PHONE, Pattern.compile("(?<!\\d)(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{3,4}(?!\\d)"));
        patterns.put(PiiPatternType.CREDIT_CARD, Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"));
        patterns.put(PiiPatternType.API_KEY, Pattern.compile("\\b(sk-[a-zA-Z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[a-zA-Z0-9]{36})\\b"));
    }

    /**
     * Detects PII in the given messages.
     *
     * @param messages the chat messages
     * @return the result containing detected patterns
     */
    public PiiDetectionResult detect(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new PiiDetectionResult(false, List.of());
        }

        List<PiiPatternType> matchedPatterns = new ArrayList<>();

        for (Message message : messages) {
            String content = message.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }

            for (Map.Entry<PiiPatternType, Pattern> entry : patterns.entrySet()) {
                if (!matchedPatterns.contains(entry.getKey()) && entry.getValue().matcher(content).find()) {
                    matchedPatterns.add(entry.getKey());
                }
            }
        }

        return new PiiDetectionResult(!matchedPatterns.isEmpty(), matchedPatterns);
    }
}
