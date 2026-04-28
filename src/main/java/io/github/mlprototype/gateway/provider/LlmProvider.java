package io.github.mlprototype.gateway.provider;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;

/**
 * Abstraction for LLM provider integrations.
 * Each provider (OpenAI, Anthropic, etc.) implements this interface.
 *
 * Extension points:
 * - Sprint 2: Add Anthropic implementation
 * - Sprint 3: isAvailable() used by Circuit Breaker
 */
public interface LlmProvider {

    /** Returns the type identifier for this provider. */
    ProviderType getType();

    /** Sends a chat completion request and returns the normalized response. */
    ChatResponse complete(ChatRequest request);

    /** Health check for this provider. Used by Circuit Breaker in Sprint 3. */
    default boolean isAvailable() {
        return true;
    }
}
