package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void testNoPiiMasking() {
        List<Message> original = List.of(new Message("user", "Hello world!"));
        List<Message> masked = masker.mask(original);

        assertThat(masked).hasSize(1);
        assertThat(masked.get(0).getContent()).isEqualTo("Hello world!");
        // Ensure it's a new list but content is the same
        assertThat(masked).isNotSameAs(original);
    }

    @Test
    void testEmailMasking() {
        List<Message> original = List.of(new Message("user", "Contact test@example.com for help."));
        List<Message> masked = masker.mask(original);

        assertThat(masked.get(0).getContent()).isEqualTo("Contact [EMAIL_REDACTED] for help.");
    }

    @Test
    void testPhoneMasking() {
        List<Message> original = List.of(new Message("user", "Call 555-123-4567."));
        List<Message> masked = masker.mask(original);

        assertThat(masked.get(0).getContent()).isEqualTo("Call [PHONE_REDACTED].");
    }

    @Test
    void testCreditCardMasking() {
        List<Message> original = List.of(new Message("user", "Card 1234-5678-9012-3456 is active."));
        List<Message> masked = masker.mask(original);

        assertThat(masked.get(0).getContent()).isEqualTo("Card [CARD_REDACTED] is active.");
    }

    @Test
    void testApiKeyMasking() {
        List<Message> original = List.of(new Message("user", "Key: sk-abcdefghijklmnopqrstuvwxyz1234567890 hidden"));
        List<Message> masked = masker.mask(original);

        assertThat(masked.get(0).getContent()).isEqualTo("Key: [KEY_REDACTED] hidden");
    }

    @Test
    void testMultiplePiiMasking() {
        List<Message> original = List.of(
                new Message("user", "Email test@test.com and phone +1-555-123-4567."),
                new Message("system", "Card: 4111-1111-1111-1111")
        );
        List<Message> masked = masker.mask(original);

        assertThat(masked).hasSize(2);
        assertThat(masked.get(0).getContent()).isEqualTo("Email [EMAIL_REDACTED] and phone [PHONE_REDACTED].");
        assertThat(masked.get(1).getContent()).isEqualTo("Card: [CARD_REDACTED]");
    }
}
