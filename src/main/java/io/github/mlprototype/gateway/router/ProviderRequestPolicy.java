package io.github.mlprototype.gateway.router;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * Validates provider-specific request constraints and prepares fallback requests.
 */
@Component
public class ProviderRequestPolicy {

    public void validatePrimary(ChatRequest request, ProviderType providerType) {
        String model = request.getModel();
        if (!isModelCompatible(model, providerType)) {
            throw ProviderRoutingException.badRequest(
                    "Model '" + model + "' is not compatible with provider '"
                            + providerType.getValue() + "'");
        }

        if (!hasRequiredMessages(request, providerType)) {
            throw ProviderRoutingException.badRequest(
                    "Anthropic requests require at least one user or assistant message");
        }
    }

    public boolean canFallback(ChatRequest request, ProviderType providerType) {
        return hasRequiredMessages(request, providerType);
    }

    public ChatRequest prepareFallback(ChatRequest request, ProviderType providerType) {
        if (isModelCompatible(request.getModel(), providerType)) {
            return request;
        }

        return ChatRequest.builder()
                .model(null)
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();
    }

    private boolean isModelCompatible(String model, ProviderType providerType) {
        if (model == null || model.isBlank()) {
            return true;
        }

        boolean claudeModel = model.toLowerCase(java.util.Locale.ROOT).startsWith("claude-");
        return providerType == ProviderType.ANTHROPIC ? claudeModel : !claudeModel;
    }

    private boolean hasRequiredMessages(ChatRequest request, ProviderType providerType) {
        if (providerType != ProviderType.ANTHROPIC) {
            return true;
        }
        if (request.getMessages() == null) {
            return false;
        }
        return request.getMessages().stream()
                .map(Message::getRole)
                .anyMatch(role -> "user".equals(role) || "assistant".equals(role));
    }
}
