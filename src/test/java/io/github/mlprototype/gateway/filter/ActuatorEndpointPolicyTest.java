package io.github.mlprototype.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

class ActuatorEndpointPolicyTest {
    @Test
    void nonLocalOnlyAllowsExplicitHealthAndInfoPaths() {
        var env = new MockEnvironment();
        env.setActiveProfiles("aws");
        var policy = new ActuatorEndpointPolicy(env);
        assertThat(policy.isPublic("/actuator/health")).isTrue();
        assertThat(policy.isPublic("/actuator/health/liveness")).isTrue();
        assertThat(policy.isPublic("/actuator/info")).isTrue();
        for (String path : java.util.List.of("/actuator", "/actuator/env", "/actuator/metrics",
                "/actuator/prometheus", "/actuator/health-secret", "/actuatorSomething")) {
            assertThat(policy.isPublic(path)).as(path).isFalse();
        }
    }

    @Test
    void localAllowsScrapingButNeverMakesNewEndpointsPublic() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local");
        var policy = new ActuatorEndpointPolicy(env);
        assertThat(policy.isPublic("/actuator/prometheus")).isTrue();
        assertThat(policy.isPublic("/actuator/metrics/jvm.memory.used")).isTrue();
        assertThat(policy.isPublic("/actuator/env")).isFalse();
        env.setActiveProfiles("local", "aws");
        assertThat(new ActuatorEndpointPolicy(env).isPublic("/actuator/prometheus")).isFalse();
    }
}
