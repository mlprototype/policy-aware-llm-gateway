package io.github.mlprototype.gateway.provider;

import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.exception.ProviderFailureType;

/** The gateway supports assistant text responses; optional usage/finish metadata may be absent. */
public final class ProviderResponseValidator {
    private ProviderResponseValidator() {}

    public static ChatResponse validate(ChatResponse response, ProviderType provider) {
        if (response == null || response.getId() == null || response.getId().isBlank()
                || response.getModel() == null || response.getModel().isBlank()
                || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().stream().anyMatch(choice -> choice == null
                    || choice.getMessage() == null
                    || !"assistant".equals(choice.getMessage().getRole())
                    || choice.getMessage().getContent() == null)) {
            throw new ProviderException(provider, ProviderFailureType.INVALID_RESPONSE, null,
                    "Invalid assistant text response from " + provider.getValue());
        }
        return response;
    }
}
