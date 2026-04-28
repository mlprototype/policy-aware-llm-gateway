package io.github.mlprototype.gateway.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
