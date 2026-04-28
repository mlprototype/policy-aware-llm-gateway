package io.github.mlprototype.gateway.security;

/**
 * ThreadLocal holder for RequestContext.
 * Lifecycle: set by ApiKeyFilter (try), cleared by ApiKeyFilter (finally).
 * All other components (RateLimitFilter, Controller) are READ-ONLY via getRequired().
 *
 * IMPORTANT: Only ApiKeyFilter may call set() and clear().
 * Virtual Threads share the same ThreadLocal semantics — clear() in finally is mandatory.
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    /**
     * Sets the request context. Called ONLY by ApiKeyFilter after successful authentication.
     */
    public static void set(RequestContext context) {
        HOLDER.set(context);
    }

    /**
     * Returns the current request context.
     * @throws IllegalStateException if no context is set (indicates a filter ordering bug)
     */
    public static RequestContext getRequired() {
        RequestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("RequestContext not set — check filter ordering");
        }
        return ctx;
    }

    /**
     * Clears the request context. Called ONLY by ApiKeyFilter in finally block.
     */
    public static void clear() {
        HOLDER.remove();
    }
}
