package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.Builder;

@Builder
public record ProviderExecutionResult(
        ProviderType requestedProvider,
        ProviderType resolvedProvider,
        boolean fallbackUsed,
        FallbackReason fallbackReason,
        ChatResponse response
) {
}
