package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single-step fallback policy for Sprint 3.
 */
@Component
public class FallbackPolicy {

    public Optional<ProviderType> fallbackFor(ProviderType primaryProvider) {
        return switch (primaryProvider) {
            case OPENAI -> Optional.of(ProviderType.ANTHROPIC);
            case ANTHROPIC -> Optional.of(ProviderType.OPENAI);
        };
    }
}
