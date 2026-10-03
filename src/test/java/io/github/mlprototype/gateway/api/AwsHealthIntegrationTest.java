package io.github.mlprototype.gateway.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;
import io.github.mlprototype.gateway.ratelimit.RateLimiter;
import io.github.mlprototype.gateway.security.VerificationBootstrapCommand;
import io.github.mlprototype.gateway.security.VerificationClientProvisioner;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.dto.Message;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.router.ProviderExecutionResult;
import io.github.mlprototype.gateway.router.ProviderRoutingService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${GATEWAY_TEST_DB_URL:jdbc:postgresql://localhost:5432/gateway_test}",
        "spring.datasource.username=gateway",
        "spring.datasource.password=gateway_test",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=9999",
        "spring.data.redis.timeout=200ms",
        "spring.data.redis.connect-timeout=200ms"
})
@ActiveProfiles("aws")
@Import(AwsHealthIntegrationTest.ProvisioningTestConfig.class)
class AwsHealthIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private ApplicationContext context;

    @Autowired private VerificationClientProvisioner provisioner;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ProviderRoutingService router;

    @TestConfiguration
    static class ProvisioningTestConfig {
        @Bean
        VerificationClientProvisioner provisioner(JdbcTemplate jdbc) {
            return new VerificationClientProvisioner(jdbc);
        }
    }

    @Test
    void missingOptionalRedisDoesNotMakeAwsHealthDown() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(rateLimiter.check("missing-redis", 60).isAvailable()).isFalse();
        assertThat(context.getBeansOfType(VerificationBootstrapCommand.class)).isEmpty();
    }

    @Test
    void awsDoesNotExposeMetricsOrPrometheus() {
        var endpoints = context.getBean(org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier.class)
                .getEndpoints().stream().map(endpoint -> endpoint.getEndpointId().toString()).toList();
        assertThat(endpoints).contains("health", "info").doesNotContain("metrics", "prometheus");
        for (String path : List.of("/actuator/metrics", "/actuator/prometheus", "/actuator/env")) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void explicitProvisioningEnablesAuthenticatedHttpChatWithoutLocalSeed() {
        String tenant = "http-verification-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", key);
        headers.set("X-Request-Id", "x".repeat(65));
        var request = new HttpEntity<>(ChatRequest.builder()
                .messages(List.of(new Message("user", "Hello"))).build(), headers);
        assertThat(rest.postForEntity("/v1/chat/completions", request, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        when(router.execute(any(), any(), any())).thenReturn(new ProviderExecutionResult(
                ProviderType.OPENAI, ProviderType.OPENAI, false, null,
                ChatResponse.builder().id("verification-response").model("m".repeat(101)).build()));
        try {
            provisioner.provision(tenant, "http-client", key);
            var response = rest.postForEntity("/v1/chat/completions", request, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("verification-response").contains("m".repeat(101));
            String trace = response.getHeaders().getFirst("X-Gateway-Trace-Id");
            assertThat(trace).hasSizeLessThanOrEqualTo(64).isNotEqualTo("x".repeat(65));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE trace_id = ?", Long.class, trace))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT model FROM audit_logs WHERE trace_id = ?", String.class, trace))
                    .isEqualTo("m".repeat(100));
            // Valid credentials still cannot reach endpoints excluded from the AWS exposure list.
            for (String path : List.of("/actuator/metrics", "/actuator/prometheus")) {
                assertThat(rest.exchange(path, org.springframework.http.HttpMethod.GET,
                        new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            }
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE tenant_id IN (SELECT CAST(id AS VARCHAR) FROM tenants WHERE name = ?)", tenant);
            jdbc.update("DELETE FROM api_clients WHERE tenant_id IN (SELECT id FROM tenants WHERE name = ?)", tenant);
            jdbc.update("DELETE FROM tenants WHERE name = ?", tenant);
        }
    }
}
