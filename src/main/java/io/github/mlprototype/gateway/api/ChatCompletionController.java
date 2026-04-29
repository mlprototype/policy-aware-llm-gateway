package io.github.mlprototype.gateway.api;

import io.github.mlprototype.gateway.audit.AuditEvent;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.content.ContentSecurityResult;
import io.github.mlprototype.gateway.content.ContentSecurityService;
import io.github.mlprototype.gateway.content.SecurityBlockException;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.filter.TraceIdFilter;
import io.github.mlprototype.gateway.router.ProviderExecutionResult;
import io.github.mlprototype.gateway.router.ProviderRoutingService;
import io.github.mlprototype.gateway.security.RequestContext;
import io.github.mlprototype.gateway.security.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI-compatible chat completion endpoint.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ChatCompletionController {

    private final ProviderRoutingService providerRoutingService;
    private final ContentSecurityService contentSecurityService;
    private final AuditLogger auditLogger;

    @PostMapping("/chat/completions")
    public ResponseEntity<ChatResponse> createChatCompletion(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = GatewayHeaders.REQUESTED_PROVIDER_HEADER, required = false) String requestedProviderHeader,
            @RequestHeader(value = GatewayHeaders.PROVIDER_HEADER, required = false) String legacyProviderHeader,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        RequestContext ctx = RequestContextHolder.getRequired();
        String traceId = (String) httpRequest.getAttribute(TraceIdFilter.MDC_TRACE_ID);
        long startTime = System.currentTimeMillis();

        ContentSecurityResult securityResult;
        try {
            securityResult = contentSecurityService.evaluate(request, ctx.piiAction(), ctx.injectionAction());
        } catch (SecurityBlockException ex) {
            long latency = System.currentTimeMillis() - startTime;
            auditLogger.log(AuditEvent.builder()
                    .traceId(traceId)
                    .tenantId(ctx.tenantId())
                    .clientId(ctx.clientId())
                    .model(request.getModel())
                    .latencyMs(latency)
                    .statusCode(ex.getStatusCode())
                    .status("blocked")
                    .errorMessage(ex.getMessage())
                    .piiDetected(ex.getDecision().piiResult().detected())
                    .piiAction(ex.getDecision().piiAction().name())
                    .piiPatterns(ex.getDecision().piiResult().matchedPatterns().toString())
                    .injectionDetected(ex.getDecision().injectionResult().detected())
                    .injectionAction(ex.getDecision().injectionAction().name())
                    .injectionRules(ex.getDecision().injectionResult().matchedRules().toString())
                    .requestHash(ex.getRequestHash())
                    .requestPreview(ex.getSanitizedPreview())
                    .build());
            throw ex;
        }

        try {
            ProviderExecutionResult executionResult = providerRoutingService.execute(
                    securityResult.effectiveRequest(),
                    requestedProviderHeader,
                    legacyProviderHeader);
            ChatResponse response = executionResult.response();
            long latency = System.currentTimeMillis() - startTime;

            httpResponse.setHeader(GatewayHeaders.PROVIDER_HEADER, executionResult.resolvedProvider().getValue());
            httpResponse.setHeader(GatewayHeaders.REQUESTED_PROVIDER_HEADER, executionResult.requestedProvider().getValue());
            httpResponse.setHeader(GatewayHeaders.FALLBACK_USED_HEADER, String.valueOf(executionResult.fallbackUsed()));

            auditLogger.log(AuditEvent.builder()
                    .traceId(traceId)
                    .tenantId(ctx.tenantId())
                    .clientId(ctx.clientId())
                    .provider(executionResult.resolvedProvider().getValue())
                    .requestedProvider(executionResult.requestedProvider().getValue())
                    .resolvedProvider(executionResult.resolvedProvider().getValue())
                    .fallbackUsed(executionResult.fallbackUsed())
                    .fallbackReason(executionResult.fallbackReason() != null
                            ? executionResult.fallbackReason().name()
                            : null)
                    .model(response.getModel())
                    .latencyMs(latency)
                    .statusCode(200)
                    .status("success")
                    .promptTokens(response.getUsage() != null ? response.getUsage().getPromptTokens() : null)
                    .completionTokens(response.getUsage() != null ? response.getUsage().getCompletionTokens() : null)
                    .totalTokens(response.getUsage() != null ? response.getUsage().getTotalTokens() : null)
                    .piiDetected(securityResult.decision().piiResult().detected())
                    .piiAction(securityResult.decision().piiAction().name())
                    .piiPatterns(securityResult.decision().piiResult().matchedPatterns().toString())
                    .injectionDetected(securityResult.decision().injectionResult().detected())
                    .injectionAction(securityResult.decision().injectionAction().name())
                    .injectionRules(securityResult.decision().injectionResult().matchedRules().toString())
                    .requestHash(securityResult.requestHash())
                    .requestPreview(securityResult.sanitizedPreview())
                    .build());

            return ResponseEntity.ok(response);
        } catch (ProviderRoutingException exception) {
            long latency = System.currentTimeMillis() - startTime;

            auditLogger.log(AuditEvent.builder()
                    .traceId(traceId)
                    .tenantId(ctx.tenantId())
                    .clientId(ctx.clientId())
                    .provider(exception.getResolvedProvider() != null ? exception.getResolvedProvider().getValue() : null)
                    .requestedProvider(exception.getRequestedProvider() != null
                            ? exception.getRequestedProvider().getValue()
                            : null)
                    .resolvedProvider(exception.getResolvedProvider() != null
                            ? exception.getResolvedProvider().getValue()
                            : null)
                    .fallbackUsed(exception.isFallbackUsed())
                    .fallbackReason(exception.getFallbackReason() != null ? exception.getFallbackReason().name() : null)
                    .model(request.getModel())
                    .latencyMs(latency)
                    .statusCode(exception.getStatusCode())
                    .status("error")
                    .errorMessage(exception.getMessage())
                    .piiDetected(securityResult.decision().piiResult().detected())
                    .piiAction(securityResult.decision().piiAction().name())
                    .piiPatterns(securityResult.decision().piiResult().matchedPatterns().toString())
                    .injectionDetected(securityResult.decision().injectionResult().detected())
                    .injectionAction(securityResult.decision().injectionAction().name())
                    .injectionRules(securityResult.decision().injectionResult().matchedRules().toString())
                    .requestHash(securityResult.requestHash())
                    .requestPreview(securityResult.sanitizedPreview())
                    .build());

            throw exception;
        }
    }
}
