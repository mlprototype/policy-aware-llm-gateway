package io.github.mlprototype.gateway.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@ActiveProfiles("test")
class GatewayMetricsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GatewayMetrics gatewayMetrics;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private io.github.mlprototype.gateway.security.AuthenticationService authenticationService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private io.github.mlprototype.gateway.audit.AuditLogger auditLogger;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private io.github.mlprototype.gateway.ratelimit.RateLimiter rateLimiter;

    @Test
    void prometheusEndpoint_shouldExposeCustomMetrics() {
        // 1. Generate some metrics
        gatewayMetrics.recordProviderRequestLatency("openai", "success", 150);
        gatewayMetrics.incrementProviderFailure("anthropic", "TIMEOUT");
        gatewayMetrics.incrementFallbackSuccess("openai", "anthropic", "PRIMARY_TIMEOUT");
        gatewayMetrics.incrementSecurityBlock("pii");
        gatewayMetrics.incrementRateLimitReject();

        // 2. Fetch the /actuator/prometheus endpoint
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        // 3. Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // 4. Verify custom metrics are present in the Prometheus text format
        // Timer format validation
        assertThat(body).contains("gateway_provider_requests_seconds_count");
        assertThat(body).contains("gateway_provider_requests_seconds_sum");
        assertThat(body).contains("provider=\"openai\"");
        assertThat(body).contains("status=\"success\"");

        // Counter format validation
        assertThat(body).contains("gateway_provider_failures_total");
        assertThat(body).contains("failure_type=\"TIMEOUT\"");
        assertThat(body).contains("provider=\"anthropic\"");

        assertThat(body).contains("gateway_routing_fallbacks_total");
        assertThat(body).contains("fallback_provider=\"anthropic\"");
        assertThat(body).contains("primary_provider=\"openai\"");
        assertThat(body).contains("reason=\"PRIMARY_TIMEOUT\"");

        assertThat(body).contains("gateway_security_blocks_total");
        assertThat(body).contains("reason=\"pii\"");

        assertThat(body).contains("gateway_ratelimit_rejects_total");

        // 5. Verify High Cardinality exclusion (no trace_id or tenant_id)
        assertThat(body).doesNotContain("trace_id=");
        assertThat(body).doesNotContain("tenant_id=");
    }
}
