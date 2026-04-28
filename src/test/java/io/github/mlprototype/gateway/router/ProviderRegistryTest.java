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

class ProviderRegistryTest {

    private ProviderRegistry providerRegistry;

    private final LlmProvider mockOpenAi = new LlmProvider() {
        @Override
        public ProviderType getType() {
            return ProviderType.OPENAI;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return null;
        }
    };

    private final LlmProvider mockAnthropic = new LlmProvider() {
        @Override
        public ProviderType getType() {
            return ProviderType.ANTHROPIC;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return null;
        }
    };

    @BeforeEach
    void setUp() {
        providerRegistry = new ProviderRegistry(List.of(mockOpenAi, mockAnthropic));
    }

    @Test
    void find_withOpenAi_returnsOpenAiProvider() {
        assertThat(providerRegistry.find(ProviderType.OPENAI))
                .isPresent()
                .get()
                .extracting(LlmProvider::getType)
                .isEqualTo(ProviderType.OPENAI);
    }

    @Test
    void find_withAnthropic_returnsAnthropicProvider() {
        assertThat(providerRegistry.find(ProviderType.ANTHROPIC))
                .isPresent()
                .get()
                .extracting(LlmProvider::getType)
                .isEqualTo(ProviderType.ANTHROPIC);
    }

    @Test
    void find_withMissingProvider_returnsEmpty() {
        ProviderRegistry emptyRegistry = new ProviderRegistry(List.of());
        assertThat(emptyRegistry.find(ProviderType.OPENAI)).isEmpty();
    }

    @Test
    void require_withMissingProvider_throwsException() {
        ProviderRegistry emptyRegistry = new ProviderRegistry(List.of());
        assertThatThrownBy(() -> emptyRegistry.require(ProviderType.OPENAI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider not registered");
    }
}
