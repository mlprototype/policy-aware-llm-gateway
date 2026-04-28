package io.github.mlprototype.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mlprototype.gateway.dto.ErrorResponse;
import io.github.mlprototype.gateway.exception.GatewayException;
import io.github.mlprototype.gateway.security.AuthenticationService;
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
 * API Key authentication filter.
 * Sprint 2: delegates to AuthenticationService for DB-based tenant auth.
 *
 * ThreadLocal lifecycle:
 *   - set(): after successful authentication (try block)
 *   - clear(): always in finally block — prevents Virtual Thread leaks
 *   - Other filters/controllers: read-only via RequestContextHolder.getRequired()
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Exclude actuator endpoints from authentication
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            RequestContext ctx = authenticationService.authenticate(apiKey);
            RequestContextHolder.set(ctx);

            // Add tenant/client info to MDC for structured logging
            MDC.put("tenantId", ctx.tenantId());
            MDC.put("clientId", ctx.clientId());

            filterChain.doFilter(request, response);

        } catch (GatewayException e) {
            log.warn("Authentication failed: {} (status={})", e.getMessage(), e.getStatusCode());
            writeErrorResponse(response, e.getStatusCode(), e.getMessage());
        } finally {
            RequestContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("clientId");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String errorLabel = switch (status) {
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            default -> "Error";
        };

        ErrorResponse error = ErrorResponse.builder()
                .status(status)
                .error(errorLabel)
                .message(message)
                .traceId(MDC.get(TraceIdFilter.MDC_TRACE_ID))
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
