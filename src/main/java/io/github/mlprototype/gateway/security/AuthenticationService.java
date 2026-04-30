package io.github.mlprototype.gateway.security;

import io.github.mlprototype.gateway.config.SecurityPolicyProperties;
import io.github.mlprototype.gateway.content.InjectionAction;
import io.github.mlprototype.gateway.content.PiiAction;
import io.github.mlprototype.gateway.exception.GatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Centralized authentication service.
 * Responsibilities:
 *   1. API Key → SHA-256 hash
 *   2. DB lookup via ApiClientRepository (status=ACTIVE only)
 *   3. Tenant status check (SUSPENDED → 403)
 *   4. RequestContext creation with resolved security policies
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final ApiClientRepository apiClientRepository;
    private final SecurityPolicyProperties securityProperties;

    /**
     * Authenticates an API key and returns a RequestContext.
     *
     * @throws GatewayException with 401 if key is invalid or client not found
     * @throws GatewayException with 403 if tenant is suspended
     */
    public RequestContext authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException("Invalid or missing API key", 401);
        }

        String hash = sha256(apiKey);

        ApiClient client = apiClientRepository.findActiveByApiKeyHash(hash)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: unknown API key");
                    return new GatewayException("Invalid or missing API key", 401);
                });

        Tenant tenant = client.getTenant();

        if ("SUSPENDED".equals(tenant.getStatus())) {
            log.warn("Authentication failed: tenant '{}' is suspended", tenant.getName());
            throw new GatewayException("Tenant is suspended", 403);
        }

        PiiAction piiAction = resolvePiiAction(tenant.getPiiAction());
        InjectionAction injectionAction = resolveInjectionAction(tenant.getInjectionAction());

        log.debug("Authenticated: tenant={}, client={}, piiAction={}, injectionAction={}",
                tenant.getName(), client.getName(), piiAction, injectionAction);

        return new RequestContext(
                tenant.getId().toString(),
                client.getId().toString(),
                tenant.getRateLimit(),
                piiAction,
                injectionAction
        );
    }

    private PiiAction resolvePiiAction(String tenantPiiAction) {
        if (tenantPiiAction != null && !tenantPiiAction.isBlank()) {
            try {
                return PiiAction.valueOf(tenantPiiAction.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant pii_action: {}", tenantPiiAction);
            }
        }
        return securityProperties.getPiiAction();
    }

    private InjectionAction resolveInjectionAction(String tenantInjectionAction) {
        if (tenantInjectionAction != null && !tenantInjectionAction.isBlank()) {
            try {
                return InjectionAction.valueOf(tenantInjectionAction.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant injection_action: {}", tenantInjectionAction);
            }
        }
        return securityProperties.getInjectionAction();
    }

    /**
     * Computes SHA-256 hex digest of the given input.
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
