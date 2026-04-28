package io.github.mlprototype.gateway.security;

/**
 * Immutable request context holding authenticated tenant/client information.
 * Set by ApiKeyFilter after successful authentication, read by downstream
 * filters and controllers via RequestContextHolder.
 *
 * Provider is intentionally NOT included — it is not determined at auth time.
 */
public record RequestContext(
        String tenantId,
        String clientId,
        int rateLimitPerMinute
) {
}
