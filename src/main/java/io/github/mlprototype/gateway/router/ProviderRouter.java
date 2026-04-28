package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes requests to the appropriate LLM provider.
 * Sprint 1: returns provider by type (OpenAI only).
 * Sprint 2: header-based routing (Anthropic added).
 * Sprint 3: Circuit Breaker + automatic fallback.
 */
@Slf4j
@Service
public class ProviderRouter {

    private final Map<ProviderType, LlmProvider> providerMap;

    public ProviderRouter(List<LlmProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(LlmProvider::getType, Function.identity()));
        log.info("Registered providers: {}", providerMap.keySet());
    }

    /**
     * Resolves the provider for the given type.
     * Falls back to OPENAI if the requested type is not available.
     */
    public LlmProvider resolve(ProviderType requestedType) {
        if (requestedType != null && providerMap.containsKey(requestedType)) {
            return providerMap.get(requestedType);
        }

        log.debug("Provider {} not available, falling back to OPENAI", requestedType);
        LlmProvider fallback = providerMap.get(ProviderType.OPENAI);
        if (fallback == null) {
            throw new IllegalStateException("No LLM provider available");
        }
        return fallback;
    }
}
