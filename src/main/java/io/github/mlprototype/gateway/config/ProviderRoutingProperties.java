package io.github.mlprototype.gateway.config;

import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway routing properties for provider selection.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.provider")
public class ProviderRoutingProperties {

    private String defaultProvider = ProviderType.OPENAI.getValue();

    public ProviderType getDefaultProviderType() {
        return ProviderType.fromValue(defaultProvider);
    }
}
