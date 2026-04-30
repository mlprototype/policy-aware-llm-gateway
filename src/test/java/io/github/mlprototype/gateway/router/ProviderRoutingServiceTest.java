package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.config.ProviderRoutingProperties;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderFailureType;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.resilience.CircuitBreakerProviderInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderRoutingServiceTest {

    @Mock
    private CircuitBreakerProviderInvoker circuitBreakerProviderInvoker;

    @Mock
    private io.github.mlprototype.gateway.observability.GatewayMetrics gatewayMetrics;

    private ProviderRoutingService providerRoutingService;
    private LlmProvider openAiProvider;
    private LlmProvider anthropicProvider;

    @BeforeEach
    void setUp() {
        openAiProvider = new StaticProvider(ProviderType.OPENAI);
        anthropicProvider = new StaticProvider(ProviderType.ANTHROPIC);

        ProviderRoutingProperties properties = new ProviderRoutingProperties();
        properties.setDefaultProvider("openai");

        providerRoutingService = new ProviderRoutingService(
                new ProviderRegistry(List.of(openAiProvider, anthropicProvider)),
                properties,
                new FallbackPolicy(),
                circuitBreakerProviderInvoker,
                gatewayMetrics);
    }

    @Test
    void execute_withoutHeader_usesDefaultProvider() {
        ChatRequest request = request();
        when(circuitBreakerProviderInvoker.invoke(any(), any())).thenReturn(response("openai-model"));

        ProviderExecutionResult result = providerRoutingService.execute(request, null, null);

        assertThat(result.requestedProvider()).isEqualTo(ProviderType.OPENAI);
        assertThat(result.resolvedProvider()).isEqualTo(ProviderType.OPENAI);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void execute_primaryTimeout_fallsBackToAnthropic() {
        ChatRequest request = request();
        when(circuitBreakerProviderInvoker.invoke(openAiProvider, request))
                .thenThrow(new ProviderException(
                        ProviderType.OPENAI,
                        ProviderFailureType.TIMEOUT,
                        null,
                        "Timed out calling openai"));
        when(circuitBreakerProviderInvoker.invoke(anthropicProvider, request))
                .thenReturn(response("anthropic-model"));

        ProviderExecutionResult result = providerRoutingService.execute(request, "openai", null);

        assertThat(result.requestedProvider()).isEqualTo(ProviderType.OPENAI);
        assertThat(result.resolvedProvider()).isEqualTo(ProviderType.ANTHROPIC);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(FallbackReason.PRIMARY_TIMEOUT);
    }

    @Test
    void execute_primary4xx_doesNotFallback() {
        ChatRequest request = request();
        when(circuitBreakerProviderInvoker.invoke(openAiProvider, request))
                .thenThrow(new ProviderException(
                        ProviderType.OPENAI,
                        ProviderFailureType.UPSTREAM_4XX,
                        400,
                        "openai client error: 400"));

        assertThatThrownBy(() -> providerRoutingService.execute(request, "openai", null))
                .isInstanceOf(ProviderRoutingException.class)
                .satisfies(exception -> assertThat(((ProviderRoutingException) exception).getStatusCode()).isEqualTo(502))
                .hasMessageContaining("client error");
    }

    @Test
    void execute_conflictingHeaders_returnsBadRequest() {
        assertThatThrownBy(() -> providerRoutingService.execute(request(), "openai", "anthropic"))
                .isInstanceOf(ProviderRoutingException.class)
                .extracting("statusCode")
                .isEqualTo(400);
    }

    private ChatRequest request() {
        return ChatRequest.builder().model("gpt-4o-mini").build();
    }

    private ChatResponse response(String model) {
        return ChatResponse.builder()
                .id("test-id")
                .model(model)
                .build();
    }

    private static final class StaticProvider implements LlmProvider {
        private final ProviderType providerType;

        private StaticProvider(ProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public ProviderType getType() {
            return providerType;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
