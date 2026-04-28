package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRouterTest {

    private ProviderRouter router;

    private final LlmProvider mockOpenAi = new LlmProvider() {
        @Override
        public ProviderType getType() { return ProviderType.OPENAI; }
        @Override
        public ChatResponse complete(ChatRequest request) { return null; }
    };

    private final LlmProvider mockAnthropic = new LlmProvider() {
        @Override
        public ProviderType getType() { return ProviderType.ANTHROPIC; }
        @Override
        public ChatResponse complete(ChatRequest request) { return null; }
    };

    @BeforeEach
    void setUp() {
        router = new ProviderRouter(List.of(mockOpenAi, mockAnthropic));
    }

    @Test
    void resolve_withOpenAi_returnsOpenAiProvider() {
        LlmProvider provider = router.resolve(ProviderType.OPENAI);
        assertThat(provider.getType()).isEqualTo(ProviderType.OPENAI);
    }

    @Test
    void resolve_withAnthropic_returnsAnthropicProvider() {
        LlmProvider provider = router.resolve(ProviderType.ANTHROPIC);
        assertThat(provider.getType()).isEqualTo(ProviderType.ANTHROPIC);
    }

    @Test
    void resolve_withNull_fallsBackToOpenAi() {
        LlmProvider provider = router.resolve(null);
        assertThat(provider.getType()).isEqualTo(ProviderType.OPENAI);
    }

    @Test
    void resolve_withMissingProvider_throwsException() {
        ProviderRouter emptyRouter = new ProviderRouter(List.of());
        assertThatThrownBy(() -> emptyRouter.resolve(ProviderType.OPENAI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No LLM provider available");
    }
}
