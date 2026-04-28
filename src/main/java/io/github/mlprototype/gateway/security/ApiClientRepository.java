package io.github.mlprototype.gateway.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    /**
     * Finds an active API client by its key hash, eagerly fetching the tenant.
     * Status=ACTIVE condition is included at the repository level to prevent
     * authentication logic from scattering status checks.
     */
    @Query("SELECT c FROM ApiClient c JOIN FETCH c.tenant WHERE c.apiKeyHash = :hash AND c.status = 'ACTIVE'")
    Optional<ApiClient> findActiveByApiKeyHash(@Param("hash") String hash);
}
