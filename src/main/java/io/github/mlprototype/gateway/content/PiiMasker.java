package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Masks Personally Identifiable Information (PII) in chat messages.
 */
@Component
public class PiiMasker {

    private final Map<PiiPatternType, Pattern> patterns = new EnumMap<>(PiiPatternType.class);
    private final Map<PiiPatternType, String> maskTokens = new EnumMap<>(PiiPatternType.class);

    public PiiMasker() {
        patterns.put(PiiPatternType.EMAIL, Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        patterns.put(PiiPatternType.PHONE, Pattern.compile("(?<!\\d)(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{3,4}(?!\\d)"));
        patterns.put(PiiPatternType.CREDIT_CARD, Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"));
        patterns.put(PiiPatternType.API_KEY, Pattern.compile("\\b(sk-[a-zA-Z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[a-zA-Z0-9]{36})\\b"));

        maskTokens.put(PiiPatternType.EMAIL, "[EMAIL_REDACTED]");
        maskTokens.put(PiiPatternType.PHONE, "[PHONE_REDACTED]");
        maskTokens.put(PiiPatternType.CREDIT_CARD, "[CARD_REDACTED]");
        maskTokens.put(PiiPatternType.API_KEY, "[KEY_REDACTED]");
    }

    /**
     * Returns a new list of messages with PII masked.
     * The original messages are not mutated.
     *
     * @param messages the chat messages
     * @return the masked chat messages
     */
    public List<Message> mask(List<Message> messages) {
        if (messages == null) {
            return null;
        }

        List<Message> maskedMessages = new ArrayList<>();
        for (Message message : messages) {
            String content = message.getContent();
            if (content != null && !content.isEmpty()) {
                String maskedContent = content;
                for (Map.Entry<PiiPatternType, Pattern> entry : patterns.entrySet()) {
                    maskedContent = entry.getValue().matcher(maskedContent).replaceAll(maskTokens.get(entry.getKey()));
                }
                maskedMessages.add(new Message(message.getRole(), maskedContent));
            } else {
                maskedMessages.add(message); // Keep as is if content is null/empty
            }
        }

        return maskedMessages;
    }
}
