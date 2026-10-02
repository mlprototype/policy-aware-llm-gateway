package io.github.mlprototype.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Explicit verification provisioning; never overwrites credentials or reactivates identities. */
@Service
@Profile("aws-bootstrap")
@RequiredArgsConstructor
public class VerificationClientProvisioner {

    private final JdbcTemplate jdbc;

    @Transactional
    public void provision(String tenantName, String clientName, String apiKey) {
        requireName(tenantName);
        requireName(clientName);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GATEWAY_API_KEY must be supplied for explicit bootstrap");
        }
        String hash = AuthenticationService.sha256(apiKey);
        var tenants = jdbc.queryForList("SELECT id, status FROM tenants WHERE name = ? FOR UPDATE", tenantName);
        UUID tenantId;
        if (tenants.isEmpty()) {
            tenantId = UUID.randomUUID();
            jdbc.update("INSERT INTO tenants (id, name, status, rate_limit) VALUES (?, ?, 'ACTIVE', 60)",
                    tenantId, tenantName);
        } else {
            var tenant = tenants.getFirst();
            if (!"ACTIVE".equals(tenant.get("status"))) {
                throw new IllegalStateException("Bootstrap refuses an inactive tenant");
            }
            tenantId = (UUID) tenant.get("id");
        }

        var clients = jdbc.queryForList(
                "SELECT api_key_hash, status FROM api_clients WHERE tenant_id = ? AND name = ?", tenantId, clientName);
        if (!clients.isEmpty()) {
            if (clients.size() != 1 || !hash.equals(clients.getFirst().get("api_key_hash"))
                    || !"ACTIVE".equals(clients.getFirst().get("status"))) {
                throw new IllegalStateException("Bootstrap refuses to overwrite or reactivate an existing client");
            }
            return;
        }
        if (jdbc.queryForObject("SELECT count(*) FROM api_clients WHERE api_key_hash = ?", Long.class, hash) != 0) {
            throw new IllegalStateException("Bootstrap key is already assigned to another client");
        }
        jdbc.update("INSERT INTO api_clients (id, tenant_id, name, api_key_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                UUID.randomUUID(), tenantId, clientName, hash);
    }

    private void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Bootstrap tenant/client names must contain 1–100 characters");
        }
    }
}
