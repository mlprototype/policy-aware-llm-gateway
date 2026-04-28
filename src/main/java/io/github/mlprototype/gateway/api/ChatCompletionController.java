package io.github.mlprototype.gateway.api;

import io.github.mlprototype.gateway.audit.AuditEvent;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.filter.LatencyFilter;
import io.github.mlprototype.gateway.filter.TraceIdFilter;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.router.ProviderRouter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI-compatible chat completion endpoint.
 * Receives requests in OpenAI format, routes to the appropriate provider,
 * and returns normalized responses with Gateway extension headers.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ChatCompletionController {

    private final ProviderRouter providerRouter;
    private final AuditLogger auditLogger;

    public static final String PROVIDER_HEADER = "X-Gateway-Provider";

    @PostMapping("/chat/completions")
    public ResponseEntity<ChatResponse> createChatCompletion(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = PROVIDER_HEADER, required = false) String providerHeader,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Resolve provider
        ProviderType providerType = null;
        if (providerHeader != null && !providerHeader.isBlank()) {
            providerType = ProviderType.fromValue(providerHeader);
        }
        LlmProvider provider = providerRouter.resolve(providerType);

        String traceId = (String) httpRequest.getAttribute(TraceIdFilter.MDC_TRACE_ID);
        long startTime = System.currentTimeMillis();

        try {
            ChatResponse response = provider.complete(request);

            long latency = System.currentTimeMillis() - startTime;

            // Set provider header
            httpResponse.setHeader(PROVIDER_HEADER, provider.getType().getValue());

            // Audit log
            auditLogger.log(AuditEvent.builder()
                    .traceId(traceId)
                    .provider(provider.getType().getValue())
                    .model(response.getModel())
                    .latencyMs(latency)
                    .statusCode(200)
                    .status("success")
                    .promptTokens(response.getUsage() != null ? response.getUsage().getPromptTokens() : null)
                    .completionTokens(response.getUsage() != null ? response.getUsage().getCompletionTokens() : null)
                    .totalTokens(response.getUsage() != null ? response.getUsage().getTotalTokens() : null)
                    .build());

            return ResponseEntity.ok(response);

        } catch (ProviderException e) {
            long latency = System.currentTimeMillis() - startTime;

            auditLogger.log(AuditEvent.builder()
                    .traceId(traceId)
                    .provider(provider.getType().getValue())
                    .model(request.getModel())
                    .latencyMs(latency)
                    .statusCode(e.getStatusCode())
                    .status("error")
                    .errorMessage(e.getMessage())
                    .build());

            throw e;
        }
    }
}
