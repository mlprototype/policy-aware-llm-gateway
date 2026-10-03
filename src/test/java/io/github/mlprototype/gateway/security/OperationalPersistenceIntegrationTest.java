package io.github.mlprototype.gateway.security;

import io.github.mlprototype.gateway.audit.AuditEvent;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.content.ContentSecurityService;
import io.github.mlprototype.gateway.content.InjectionAction;
import io.github.mlprototype.gateway.content.PiiAction;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import io.github.mlprototype.gateway.exception.GatewayException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${GATEWAY_TEST_DB_URL:jdbc:postgresql://localhost:5432/gateway_test}",
        "spring.datasource.username=gateway",
        "spring.datasource.password=gateway_test"
})
@ActiveProfiles({"aws", "aws-bootstrap"})
@Transactional
class OperationalPersistenceIntegrationTest {

    @MockitoBean
    private VerificationBootstrapCommand command;
    @Autowired private VerificationClientProvisioner provisioner;
    @Autowired private AuthenticationService authentication;
    @Autowired private ContentSecurityService security;
    @Autowired private AuditLogger audit;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(ints = {199, 200, 201, 1000})
    void generatedPreviewPersistsWithinExistingColumn(int length) {
        var request = ChatRequest.builder().messages(List.of(new Message("user", "あ".repeat(length)))).build();
        String preview = security.evaluate(request, PiiAction.MASK, InjectionAction.BLOCK).sanitizedPreview();
        assertPersistedPreview(preview, preview);
    }

    @Test
    void maskedLongPreviewPersists() {
        var request = ChatRequest.builder()
                .messages(List.of(new Message("user", "test@example.com " + "あ".repeat(1000)))).build();
        String preview = security.evaluate(request, PiiAction.MASK, InjectionAction.BLOCK).sanitizedPreview();
        assertThat(preview).hasSize(200).doesNotContain("test@example.com");
        assertPersistedPreview(preview, preview);
    }

    @Test
    void auditDefensivelyBoundsPreviewFromOtherEventProducers() {
        assertPersistedPreview("あ".repeat(1000), "あ".repeat(197) + "...");
    }

    @Test
    void explicitProvisioningEnablesDbAuthenticationAndIsIdempotent() {
        String tenant = "verification-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> authentication.authenticate(key)).isInstanceOf(GatewayException.class);

        provisioner.provision(tenant, "client", key);
        RequestContext context = authentication.authenticate(key);
        provisioner.provision(tenant, "client", key);

        assertThat(authentication.authenticate(key)).isEqualTo(context);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_clients WHERE tenant_id = ?", Long.class,
                UUID.fromString(context.tenantId()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT api_key_hash FROM api_clients WHERE id = ?", String.class,
                UUID.fromString(context.clientId()))).isEqualTo(AuthenticationService.sha256(key)).isNotEqualTo(key);
        assertThat(context.piiAction()).isEqualTo(PiiAction.MASK);
        assertThat(context.injectionAction()).isEqualTo(InjectionAction.BLOCK);
    }

    @Test
    void provisioningDoesNotOverwriteCredentialsOrReactivateIdentities() {
        String tenant = "verification-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        provisioner.provision(tenant, "client", key);
        RequestContext context = authentication.authenticate(key);
        assertThatThrownBy(() -> provisioner.provision(tenant, "client", "replacement-key"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overwrite");
        assertThat(authentication.authenticate(key).clientId()).isEqualTo(context.clientId());

        jdbc.update("UPDATE api_clients SET status = 'REVOKED' WHERE id = ?", UUID.fromString(context.clientId()));
        assertThatThrownBy(() -> provisioner.provision(tenant, "client", key))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reactivate");
        jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", UUID.fromString(context.tenantId()));
        assertThatThrownBy(() -> provisioner.provision(tenant, "other-client", "other-key"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive tenant");
    }

    @Test
    void provisioningRejectsBlankKeyAndKeyAssignedToAnotherClient() {
        String tenant = "verification-" + UUID.randomUUID();
        assertThatThrownBy(() -> provisioner.provision(tenant, "client", ""))
                .isInstanceOf(IllegalArgumentException.class);
        String key = UUID.randomUUID().toString();
        provisioner.provision(tenant, "client", key);
        assertThatThrownBy(() -> provisioner.provision(tenant, "other-client", key))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("another client");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "success,100", "success,101", "success,1000",
            "blocked,100", "blocked,101", "blocked,1000",
            "error,100", "error,101", "error,1000"
    })
    void auditModelFitsExistingColumnForEveryEventStatus(String status, int length) {
        String trace = UUID.randomUUID().toString();
        String model = "m".repeat(length);
        var event = AuditEvent.builder().traceId(trace).tenantId("test-tenant").clientId("test-client")
                .status(status).statusCode(status.equals("success") ? 200 : 400).model(model).build();
        audit.log(event);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT model FROM audit_logs WHERE trace_id = ?", String.class, trace))
                .isEqualTo(model.substring(0, Math.min(length, 100)));
        assertThat(event.getModel()).isEqualTo(model);
    }

    private void assertPersistedPreview(String input, String expected) {
        String trace = UUID.randomUUID().toString();
        audit.log(AuditEvent.builder().traceId(trace).tenantId("test-tenant").clientId("test-client")
                .status("success").statusCode(200).requestPreview(input).build());
        entityManager.flush();
        String stored = jdbc.queryForObject("SELECT request_preview FROM audit_logs WHERE trace_id = ?",
                String.class, trace);
        assertThat(stored).isEqualTo(expected).hasSizeLessThanOrEqualTo(200);
    }
}
