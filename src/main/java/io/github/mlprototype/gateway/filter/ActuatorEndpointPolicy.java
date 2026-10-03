package io.github.mlprototype.gateway.filter;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit public operational endpoints; adding an Actuator endpoint does not bypass authentication. */
@Component
public class ActuatorEndpointPolicy {
    private final boolean localMonitoring;

    public ActuatorEndpointPolicy(Environment environment) {
        localMonitoring = environment.matchesProfiles("local")
                && !environment.matchesProfiles("aws", "aws-bootstrap");
    }

    public boolean isPublic(String path) {
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info")
                || (localMonitoring && (path.equals("/actuator/prometheus")
                    || path.equals("/actuator/metrics") || path.startsWith("/actuator/metrics/")));
    }
}
