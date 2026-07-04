package io.github.mlprototype.gateway.api;

import io.github.mlprototype.gateway.audit.AuditEvent;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.content.ContentSecurityResult;
import io.github.mlprototype.gateway.content.ContentSecurityService;
import io.github.mlprototype.gateway.content.SecurityBlockException;
import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.dto.ErrorResponse;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.filter.TraceIdFilter;
import io.github.mlprototype.gateway.router.ProviderExecutionResult;
import io.github.mlprototype.gateway.router.ProviderRoutingService;
import io.github.mlprototype.gateway.security.RequestContext;
import io.github.mlprototype.gateway.security.RequestContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "チャット補完", description = "OpenAI 互換のチャット補完エンドポイント")
public class ChatCompletionController {

    private final ProviderRoutingService providerRoutingService;
    private final ContentSecurityService contentSecurityService;
    private final AuditLogger auditLogger;

    @PostMapping("/chat/completions")
    @Operation(
            summary = "チャット補完を作成する",
            description = "日本語メッセージを含む OpenAI 互換リクエストを Gateway 経由でルーティングします。レスポンスヘッダーで trace、latency、provider routing、rate limit、fallback、security block の情報を確認できます。",
            security = @SecurityRequirement(name = "gatewayApiKey"),
            parameters = {
                    @Parameter(
                            name = TraceIdFilter.REQUEST_ID_HEADER,
                            description = "任意のクライアント指定トレース ID。省略時は Gateway が生成します。",
                            in = ParameterIn.HEADER
                    )
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "OpenAI 互換のチャット補完リクエスト。content には日本語をそのまま指定できます。",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatRequest.class),
                            examples = @ExampleObject(
                                    name = "日本語リクエスト",
                                    value = """
                                            {
                                              "model": "gpt-4o-mini",
                                              "messages": [
                                                {
                                                  "role": "system",
                                                  "content": "あなたは優秀なカスタマーサポートアシスタントです。ユーザーからの問い合わせ内容を分析し、対応優先度（高/中/低）と要約を簡潔な日本語で出力してください。"
                                                },
                                                {
                                                  "role": "user",
                                                  "content": "【問い合わせ内容】システム移行後から管理画面にログインできなくなりました。「認証エラー」と表示されます。業務への影響が大きいため、至急原因と対策をご連絡ください。"
                                                }
                                              ],
                                              "temperature": 0.2,
                                              "max_tokens": 512
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "チャット補完の生成に成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatResponse.class),
                            examples = @ExampleObject(
                                    name = "chatCompletion",
                                    summary = "日本語でのチャット補完結果",
                                    value = OpenApiExamples.CHAT_COMPLETION_SUCCESS
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "リクエスト形式が不正、または PII ポリシーによりブロック",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "リクエストのバリデーションエラー",
                                            value = OpenApiExamples.BAD_REQUEST_VALIDATION
                                    ),
                                    @ExampleObject(
                                            name = "piiBlocked",
                                            summary = "PII ポリシーによるブロック",
                                            value = OpenApiExamples.BAD_REQUEST_PII_BLOCK
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "API Key が未指定、または不正",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "invalidApiKey",
                                    summary = "API Key が未指定、または不正",
                                    value = OpenApiExamples.UNAUTHORIZED
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "認証済みだが Gateway の利用が許可されていない、または Prompt Injection ポリシーによりブロック",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "tenantSuspended",
                                            summary = "テナントが停止状態",
                                            value = OpenApiExamples.FORBIDDEN_TENANT_SUSPENDED
                                    ),
                                    @ExampleObject(
                                            name = "promptInjectionBlocked",
                                            summary = "Prompt Injection ポリシーによるブロック",
                                            value = OpenApiExamples.FORBIDDEN_INJECTION_BLOCK
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "レートリミット超過",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "rateLimitExceeded",
                                    summary = "テナントのリクエスト上限超過",
                                    value = OpenApiExamples.RATE_LIMIT_EXCEEDED
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "プロバイダの upstream エラー、または不正なレスポンス",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "upstreamError",
                                            summary = "プロバイダの upstream エラー",
                                            value = OpenApiExamples.BAD_GATEWAY_UPSTREAM
                                    ),
                                    @ExampleObject(
                                            name = "invalidProviderResponse",
                                            summary = "プロバイダレスポンスが不正",
                                            value = OpenApiExamples.BAD_GATEWAY_INVALID_RESPONSE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "プロバイダ利用不可、タイムアウト、または Circuit Breaker open",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "providerTimeout",
                                            summary = "プロバイダ呼び出しがタイムアウト",
                                            value = OpenApiExamples.SERVICE_UNAVAILABLE_TIMEOUT
                                    ),
                                    @ExampleObject(
                                            name = "circuitBreakerOpen",
                                            summary = "Circuit Breaker が open",
                                            value = OpenApiExamples.SERVICE_UNAVAILABLE_CIRCUIT_BREAKER
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<ChatResponse> createChatCompletion(
            @Valid @RequestBody ChatRequest request,
            @Parameter(
                    name = GatewayHeaders.REQUESTED_PROVIDER_HEADER,
                    description = "利用したいプロバイダ。指定可能な値: openai, anthropic。省略時は Gateway のデフォルト設定を使用します。",
                    in = ParameterIn.HEADER
            )
            @RequestHeader(value = GatewayHeaders.REQUESTED_PROVIDER_HEADER, required = false) String requestedProviderHeader,
            @Parameter(hidden = true)
            @RequestHeader(value = GatewayHeaders.PROVIDER_HEADER, required = false) String legacyProviderHeader,
            @Parameter(hidden = true)
            HttpServletRequest httpRequest,
            @Parameter(hidden = true)
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
                    .injectionScore(ex.getDecision().injectionResult().score())
                    .injectionCategories(formatInjectionCategories(ex.getDecision().injectionResult()))
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
                    // 監査ログ上のステータスとして、ゲートウェイ自体の処理が正常に完了したことを示すために200(OK)を記録します。
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
                    .injectionScore(securityResult.decision().injectionResult().score())
                    .injectionCategories(formatInjectionCategories(securityResult.decision().injectionResult()))
                    .requestHash(securityResult.requestHash())
                    .requestPreview(securityResult.sanitizedPreview())
                    .build());

            return ResponseEntity.ok(response);
        } catch (ProviderRoutingException exception) {
            // プロバイダーへのリクエスト（フォールバック含む）が最終的に失敗した場合に、
            // どのプロバイダーでどのようなエラーが発生したかの詳細を監査ログに記録するためここで捕捉します。
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
                    .injectionScore(securityResult.decision().injectionResult().score())
                    .injectionCategories(formatInjectionCategories(securityResult.decision().injectionResult()))
                    .requestHash(securityResult.requestHash())
                    .requestPreview(securityResult.sanitizedPreview())
                    .build());

            throw exception;
        }
    }

    private String formatInjectionCategories(io.github.mlprototype.gateway.content.InjectionDetectionResult result) {
        return result.categories().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(","));
    }
}
