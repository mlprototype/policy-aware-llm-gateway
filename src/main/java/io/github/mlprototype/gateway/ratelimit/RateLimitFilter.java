package io.github.mlprototype.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mlprototype.gateway.dto.ErrorResponse;
import io.github.mlprototype.gateway.filter.TraceIdFilter;
import io.github.mlprototype.gateway.security.RequestContext;
import io.github.mlprototype.gateway.security.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limit filter using fixed-window algorithm via Redis.
 * Runs AFTER ApiKeyFilter (needs RequestContext with tenant info).
 *
 * Behavior:
 *   - Normal: adds X-RateLimit-Limit and X-RateLimit-Remaining headers
 *   - Exceeded: returns 429 directly (no exception thrown)
 *   - Redis down: fail-open, no X-RateLimit-* headers
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        RequestContext ctx;
        try {
            ctx = RequestContextHolder.getRequired();
        } catch (IllegalStateException e) {
            // No context means auth filter rejected — just pass through
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter.RateLimitResult result = rateLimiter.check(
                ctx.tenantId(), ctx.rateLimitPerMinute());

        // Store result for audit logging downstream
        request.setAttribute("rateLimitResult", result.toAuditString());

        if (result.isRejected()) {
            log.warn("Rate limit exceeded: tenant={}, limit={}/min",
                    ctx.tenantId(), result.limit());

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            // Fixed window simplification: 60s hardcoded. Future improvement: return actual window remainder.
            response.setHeader("Retry-After", "60");

            ErrorResponse error = ErrorResponse.builder()
                    .status(429)
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Limit: " + result.limit() + " requests/min")
                    .traceId(MDC.get(TraceIdFilter.MDC_TRACE_ID))
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        // Add rate limit headers only when Redis is available
        if (result.isAvailable()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        }

        filterChain.doFilter(request, response);
    }
}
