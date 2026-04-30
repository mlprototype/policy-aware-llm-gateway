package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectorTest {

    private final PiiDetector detector = new PiiDetector();

    @Test
    void testNoPii() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "Hello, how are you?"),
                new Message("system", "I am a helpful assistant.")
        ));

        assertThat(result.detected()).isFalse();
        assertThat(result.matchedPatterns()).isEmpty();
    }

    @Test
    void testEmailDetection() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "My email is test.user123@example.com.")
        ));

        assertThat(result.detected()).isTrue();
        assertThat(result.matchedPatterns()).containsExactly(PiiPatternType.EMAIL);
    }

    @Test
    void testPhoneDetection() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "Call me at +1-555-019-8372.")
        ));

        assertThat(result.detected()).isTrue();
        assertThat(result.matchedPatterns()).containsExactly(PiiPatternType.PHONE);
    }

    @Test
    void testCreditCardDetection() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "Here is my card: 4111-1111-1111-1111")
        ));

        assertThat(result.detected()).isTrue();
        assertThat(result.matchedPatterns()).contains(PiiPatternType.CREDIT_CARD);
    }

    @Test
    void testApiKeyDetection() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "My key: sk-abcdefghijklmnopqrstuvwxyz1234567890")
        ));

        assertThat(result.detected()).isTrue();
        assertThat(result.matchedPatterns()).contains(PiiPatternType.API_KEY);
    }

    @Test
    void testMultiplePiiDetection() {
        PiiDetectionResult result = detector.detect(List.of(
                new Message("user", "Contact test@test.com or call 555-123-4567. Card: 1234-5678-9012-3456")
        ));

        assertThat(result.detected()).isTrue();
        assertThat(result.matchedPatterns()).contains(
                PiiPatternType.EMAIL, PiiPatternType.PHONE, PiiPatternType.CREDIT_CARD);
    }

    @Test
    void testEmptyMessages() {
        PiiDetectionResult result = detector.detect(List.of());
        assertThat(result.detected()).isFalse();

        PiiDetectionResult resultNullContent = detector.detect(List.of(new Message("user", null), new Message("user", "")));
        assertThat(resultNullContent.detected()).isFalse();
    }
}
