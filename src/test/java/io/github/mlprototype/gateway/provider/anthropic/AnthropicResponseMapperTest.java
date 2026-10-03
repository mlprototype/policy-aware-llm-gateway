package io.github.mlprototype.gateway.provider.anthropic;

import io.github.mlprototype.gateway.provider.ProviderResponseValidator;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.exception.ProviderException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AnthropicResponseMapperTest {
    private final AnthropicResponseMapper mapper = new AnthropicResponseMapper();

    @Test
    void singleTextBlock() {
        assertThat(text(List.of(block("Hello")))).isEqualTo("Hello");
    }

    @Test
    void preservesEveryBlockInOrderWithoutAddingSeparatorsOrDeduplicating() {
        assertThat(text(List.of(block("first"), block(" second"), block("first"))))
                .isEqualTo("first secondfirst");
    }

    @Test
    void skipsNonTextBlocksWhilePreservingSurroundingText() {
        assertThat(text(List.of(block("before"), Map.of("type", "thinking", "thinking", "private"), block("after"))))
                .isEqualTo("beforeafter");
    }

    @Test
    void noTextBlocksCannotBecomeSuccessfulAssistantTextResponse() {
        var response = mapper.toChatResponse(Map.of("id", "id", "model", "model",
                "content", List.of(Map.of("type", "tool_use"))));
        assertThatThrownBy(() -> ProviderResponseValidator.validate(response, ProviderType.ANTHROPIC))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void missingTextValueIsMalformedRatherThanAppendedAsNull() {
        assertThatThrownBy(() -> text(List.of(Map.of("type", "text"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Map<String, Object> block(String text) { return Map.of("type", "text", "text", text); }
    private String text(List<Map<String, Object>> blocks) {
        return mapper.toChatResponse(Map.of("id", "id", "model", "model", "content", blocks))
                .getChoices().getFirst().getMessage().getContent();
    }
}
