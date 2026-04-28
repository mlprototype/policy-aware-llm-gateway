package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of available provider implementations.
 */
@Slf4j
@Service
public class ProviderRegistry {

    private final Map<ProviderType, LlmProvider> providerMap;

    public ProviderRegistry(List<LlmProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(LlmProvider::getType, Function.identity()));
        log.info("Registered providers: {}", providerMap.keySet());
    }

    public Optional<LlmProvider> find(ProviderType providerType) {
        return Optional.ofNullable(providerMap.get(providerType));
    }

    public LlmProvider require(ProviderType providerType) {
        return find(providerType)
                .orElseThrow(() -> new IllegalStateException("Provider not registered: " + providerType));
    }
}
