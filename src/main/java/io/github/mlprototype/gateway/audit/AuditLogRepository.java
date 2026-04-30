package io.github.mlprototype.gateway.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for saving audit logs to the database.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
