package io.github.mlprototype.gateway.config;

import io.github.mlprototype.gateway.content.InjectionAction;
import io.github.mlprototype.gateway.content.PiiAction;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Global default security policy configuration.
 * Read from gateway.security in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.security")
public class SecurityPolicyProperties {
    private PiiAction piiAction = PiiAction.MASK;
    private InjectionAction injectionAction = InjectionAction.WARN;
}
