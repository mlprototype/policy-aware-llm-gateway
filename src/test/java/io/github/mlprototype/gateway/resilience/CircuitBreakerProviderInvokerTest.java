package io.github.mlprototype.gateway.resilience;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderFailureType;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerProviderInvokerTest {

    private CircuitBreakerProviderInvoker invoker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom().build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker("openai");
        registry.circuitBreaker("anthropic");
        io.github.mlprototype.gateway.observability.GatewayMetrics gatewayMetrics = org.mockito.Mockito.mock(io.github.mlprototype.gateway.observability.GatewayMetrics.class);
        invoker = new CircuitBreakerProviderInvoker(registry, gatewayMetrics);
    }

    @Test
    void invoke_whenClosed_returnsResponse() {
        ChatResponse response = ChatResponse.builder().id("ok").build();

        ChatResponse result = invoker.invoke(new TestProvider(ProviderType.OPENAI, response), ChatRequest.builder().build());

        assertThat(result.getId()).isEqualTo("ok");
    }

    @Test
    void invoke_whenOpen_throwsBreakerOpenProviderException() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("openai").transitionToOpenState();
        io.github.mlprototype.gateway.observability.GatewayMetrics gatewayMetrics = org.mockito.Mockito.mock(io.github.mlprototype.gateway.observability.GatewayMetrics.class);
        CircuitBreakerProviderInvoker openInvoker = new CircuitBreakerProviderInvoker(registry, gatewayMetrics);

        assertThatThrownBy(() -> openInvoker.invoke(
                new TestProvider(ProviderType.OPENAI, ChatResponse.builder().build()),
                ChatRequest.builder().build()))
                .isInstanceOf(ProviderException.class)
                .extracting("failureType")
                .isEqualTo(ProviderFailureType.BREAKER_OPEN);
    }

    private record TestProvider(ProviderType type, ChatResponse response) implements LlmProvider {
        @Override
        public ProviderType getType() {
            return type;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return response;
        }
    }
}
