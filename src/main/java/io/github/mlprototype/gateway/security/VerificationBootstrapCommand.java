package io.github.mlprototype.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Run only with the explicit aws-bootstrap profile, as a separate non-web process. */
@Component
@Profile("aws-bootstrap")
@RequiredArgsConstructor
public class VerificationBootstrapCommand implements ApplicationRunner {

    private final VerificationClientProvisioner provisioner;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        try {
            provisioner.provision(
                    environment.getRequiredProperty("gateway.bootstrap.tenant-name"),
                    environment.getRequiredProperty("gateway.bootstrap.client-name"),
                    environment.getProperty("GATEWAY_API_KEY"));
        } finally {
            context.close();
        }
        System.out.println("GATEWAY_BOOTSTRAP_OK");
    }
}
