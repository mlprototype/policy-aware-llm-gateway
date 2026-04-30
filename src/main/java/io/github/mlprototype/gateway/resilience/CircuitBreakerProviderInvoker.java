package io.github.mlprototype.gateway.resilience;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderFailureType;
import io.github.mlprototype.gateway.observability.GatewayMetrics;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Applies provider-scoped circuit breakers around upstream calls.
 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerProviderInvoker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final GatewayMetrics gatewayMetrics;

    public ChatResponse invoke(LlmProvider provider, ChatRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.getType().getValue());
        Supplier<ChatResponse> supplier = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> provider.complete(request));

        long startTime = System.currentTimeMillis();
        try {
            ChatResponse response = supplier.get();
            long latency = System.currentTimeMillis() - startTime;
            gatewayMetrics.recordProviderRequestLatency(provider.getType().getValue(), "success", latency);
            return response;
        } catch (CallNotPermittedException exception) {
            long latency = System.currentTimeMillis() - startTime;
            gatewayMetrics.recordProviderRequestLatency(provider.getType().getValue(), "error", latency);
            gatewayMetrics.incrementProviderFailure(provider.getType().getValue(), ProviderFailureType.BREAKER_OPEN.name());
            throw new ProviderException(
                    provider.getType(),
                    ProviderFailureType.BREAKER_OPEN,
                    null,
                    "Circuit breaker open for " + provider.getType().getValue(),
                    exception);
        } catch (ProviderException exception) {
            long latency = System.currentTimeMillis() - startTime;
            gatewayMetrics.recordProviderRequestLatency(provider.getType().getValue(), "error", latency);
            gatewayMetrics.incrementProviderFailure(provider.getType().getValue(), exception.getFailureType().name());
            throw exception;
        } catch (Exception exception) {
            long latency = System.currentTimeMillis() - startTime;
            gatewayMetrics.recordProviderRequestLatency(provider.getType().getValue(), "error", latency);
            gatewayMetrics.incrementProviderFailure(provider.getType().getValue(), "UNKNOWN");
            throw exception;
        }
    }
}

