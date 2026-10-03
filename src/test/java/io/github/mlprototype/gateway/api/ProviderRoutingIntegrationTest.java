package io.github.mlprototype.gateway.api;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderRoutingIntegrationTest {

    private static final MockWebServer OPENAI_SERVER = startServer();
    private static final MockWebServer ANTHROPIC_SERVER = startServer();

    @Autowired
    private MockMvc mockMvc;
    @Autowired private io.github.mlprototype.gateway.provider.openai.OpenAiProvider openAiProvider;
    @Autowired private io.github.mlprototype.gateway.provider.anthropic.AnthropicProvider anthropicProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private io.github.mlprototype.gateway.security.AuthenticationService authenticationService;

    @MockitoBean
    private io.github.mlprototype.gateway.content.ContentSecurityService contentSecurityService;

    @MockitoBean
    private io.github.mlprototype.gateway.audit.AuditLogger auditLogger;

    @MockitoBean
    private io.github.mlprototype.gateway.ratelimit.RateLimiter rateLimiter;

    @DynamicPropertySource
    static void overrideProviderUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.openai.base-url", () -> OPENAI_SERVER.url("/v1").toString());
        registry.add("gateway.provider.anthropic.base-url", () -> ANTHROPIC_SERVER.url("/").toString());
        registry.add("gateway.provider.openai.api-key", () -> "test-openai-key");
        registry.add("gateway.provider.anthropic.api-key", () -> "test-anthropic-key");
    }

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("openai").reset();
        circuitBreakerRegistry.circuitBreaker("anthropic").reset();

        when(authenticationService.authenticate("test-gateway-key"))
                .thenReturn(new io.github.mlprototype.gateway.security.RequestContext("tenant-test", "client-test", 60, io.github.mlprototype.gateway.content.PiiAction.MASK, io.github.mlprototype.gateway.content.InjectionAction.WARN));
        when(authenticationService.authenticate(org.mockito.ArgumentMatchers.argThat(arg -> !"test-gateway-key".equals(arg))))
                .thenThrow(new io.github.mlprototype.gateway.exception.GatewayException("Invalid or missing API key", 401));

        when(contentSecurityService.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    io.github.mlprototype.gateway.dto.ChatRequest req = invocation.getArgument(0);
                    return new io.github.mlprototype.gateway.content.ContentSecurityResult(
                            new io.github.mlprototype.gateway.content.SecurityDecision(
                                    new io.github.mlprototype.gateway.content.PiiDetectionResult(false, java.util.List.of()),
                                    io.github.mlprototype.gateway.content.PiiAction.MASK,
                                    io.github.mlprototype.gateway.content.InjectionDetectionResult.none(),
                                    io.github.mlprototype.gateway.content.InjectionAction.WARN),
                            req,
                            "preview",
                            "hash");
                });

        when(rateLimiter.check(anyString(), anyInt()))
                .thenReturn(new io.github.mlprototype.gateway.ratelimit.RateLimiter.RateLimitResult(
                        io.github.mlprototype.gateway.ratelimit.RateLimiter.RateLimitResult.Status.ALLOWED, 60, 59));
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("openai").reset();
        circuitBreakerRegistry.circuitBreaker("anthropic").reset();
    }

    @AfterAll
    static void shutdown() throws IOException {
        OPENAI_SERVER.shutdown();
        ANTHROPIC_SERVER.shutdown();
    }

    @Test
    void provider5xx_fallsBackToAnthropic() throws Exception {
        OPENAI_SERVER.enqueue(jsonResponse(503, "{\"error\":\"upstream unavailable\"}"));
        ANTHROPIC_SERVER.enqueue(jsonResponse(200, """
                {
                  "id": "msg_123",
                  "model": "claude-haiku-4-5-20251001",
                  "content": [{"type":"text","text":"fallback-ok"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 4, "output_tokens": 2}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai")
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai"))
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "anthropic"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "true"))
                .andExpect(jsonPath("$.choices[0].message.content").value("fallback-ok"));
    }

    @Test
    void provider4xx_doesNotFallbackAndReturns502() throws Exception {
        int anthropicRequestsBefore = ANTHROPIC_SERVER.getRequestCount();
        OPENAI_SERVER.enqueue(jsonResponse(400, "{\"error\":\"bad request\"}"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai")
                        .content(requestBody()))
                .andExpect(status().isBadGateway())
                .andExpect(header().string(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai"))
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "openai"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "false"))
                .andExpect(jsonPath("$.message").value("openai client error: 400 - bad request"));

        org.assertj.core.api.Assertions.assertThat(ANTHROPIC_SERVER.getRequestCount())
                .isEqualTo(anthropicRequestsBefore);
    }

    @Test
    void breakerOpen_fallsBackImmediately() throws Exception {
        int openAiRequestsBefore = OPENAI_SERVER.getRequestCount();
        circuitBreakerRegistry.circuitBreaker("openai").transitionToOpenState();
        ANTHROPIC_SERVER.enqueue(jsonResponse(200, """
                {
                  "id": "msg_456",
                  "model": "claude-haiku-4-5-20251001",
                  "content": [{"type":"text","text":"breaker-fallback"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 4, "output_tokens": 2}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai")
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "anthropic"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "true"))
                .andExpect(jsonPath("$.choices[0].message.content").value("breaker-fallback"));

        org.assertj.core.api.Assertions.assertThat(OPENAI_SERVER.getRequestCount())
                .isEqualTo(openAiRequestsBefore);
    }

    @Test
    void anthropicRequest_withCompatibleModelAndUserMessage_returns200() throws Exception {
        ANTHROPIC_SERVER.enqueue(jsonResponse(200, """
                {
                  "id": "msg_anthropic",
                  "model": "claude-haiku-4-5-20251001",
                  "content": [{"type":"text","text":"こんにちは。お手伝いします。"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 8, "output_tokens": 6}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "anthropic")
                        .content("""
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "messages": [{"role": "user", "content": "こんにちは"}],
                                  "max_tokens": 32
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "anthropic"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "false"))
                .andExpect(jsonPath("$.choices[0].message.content").value("こんにちは。お手伝いします。"));
    }

    @Test
    void anthropicRequest_withOpenAiModel_returns400BeforeProviderCall() throws Exception {
        int requestsBefore = ANTHROPIC_SERVER.getRequestCount();

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "anthropic")
                        .content(requestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Model 'gpt-4o-mini' is not compatible with provider 'anthropic'"));

        org.assertj.core.api.Assertions.assertThat(ANTHROPIC_SERVER.getRequestCount())
                .isEqualTo(requestsBefore);
    }

    @Test
    void anthropicRequest_withOnlySystemMessage_returns400BeforeProviderCall() throws Exception {
        int requestsBefore = ANTHROPIC_SERVER.getRequestCount();

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType("application/json")
                        .header("X-API-Key", "test-gateway-key")
                        .header(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "anthropic")
                        .content("""
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "messages": [{"role": "system", "content": "簡潔に答えてください"}],
                                  "max_tokens": 32
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Anthropic requests require at least one user or assistant message"));

        org.assertj.core.api.Assertions.assertThat(ANTHROPIC_SERVER.getRequestCount())
                .isEqualTo(requestsBefore);
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidResponses")
    void invalidHttp200Is502AndNeverFallsBack(String provider, String body) throws Exception {
        MockWebServer server = provider.equals("openai") ? OPENAI_SERVER : ANTHROPIC_SERVER;
        MockWebServer other = provider.equals("openai") ? ANTHROPIC_SERVER : OPENAI_SERVER;
        int otherRequests = other.getRequestCount();
        server.enqueue(jsonResponse(200, body));
        mockMvc.perform(post("/v1/chat/completions").contentType("application/json")
                .header("X-API-Key", "test-gateway-key")
                .header("X-Gateway-Requested-Provider", provider)
                .content(requestBody().replace("\"model\": \"gpt-4o-mini\",", "")))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("X-Gateway-Fallback-Used", "false"));
        org.assertj.core.api.Assertions.assertThat(other.getRequestCount()).isEqualTo(otherRequests);
        org.assertj.core.api.Assertions.assertThat(circuitBreakerRegistry.circuitBreaker(provider)
                .getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"openai,400", "anthropic,400", "openai,503", "anthropic,503"})
    void bothProvidersPreserveUpstreamErrorClassification(String provider, int statusCode) {
        (provider.equals("openai") ? OPENAI_SERVER : ANTHROPIC_SERVER)
                .enqueue(jsonResponse(statusCode, "{\"error\":{\"message\":\"fixture failure\"}}"));
        io.github.mlprototype.gateway.provider.LlmProvider selected = provider.equals("openai")
                ? openAiProvider : anthropicProvider;
        var request = io.github.mlprototype.gateway.dto.ChatRequest.builder()
                .messages(java.util.List.of(new io.github.mlprototype.gateway.dto.Message("user", "Hello"))).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> selected.complete(request))
                .isInstanceOfSatisfying(io.github.mlprototype.gateway.exception.ProviderException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getFailureType()).isEqualTo(statusCode == 400
                            ? io.github.mlprototype.gateway.exception.ProviderFailureType.UPSTREAM_4XX
                            : io.github.mlprototype.gateway.exception.ProviderFailureType.UPSTREAM_5XX);
                    org.assertj.core.api.Assertions.assertThat(ex.isFallbackEligible()).isEqualTo(statusCode == 503);
                });
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidResponses() {
        var result = new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (String provider : java.util.List.of("openai", "anthropic")) {
            for (String body : java.util.List.of("", "null", "{}", "[]", "{malformed}",
                    "{\"id\":\"id\",\"model\":\"model\"}")) {
                result.add(org.junit.jupiter.params.provider.Arguments.of(provider, body));
            }
        }
        for (String choices : java.util.List.of("[]", "[null]", "[{\"message\":{\"role\":\"assistant\"}}]",
                "[{\"message\":{\"role\":\"user\",\"content\":\"text\"}}]")) {
            result.add(org.junit.jupiter.params.provider.Arguments.of("openai",
                    "{\"id\":\"id\",\"model\":\"model\",\"choices\":" + choices + "}"));
        }
        for (String content : java.util.List.of("[]", "[null]", "[{\"type\":\"tool_use\"}]",
                "[{\"type\":\"text\"}]", "[{\"type\":\"text\",\"text\":1}]", "\"not-array\"")) {
            result.add(org.junit.jupiter.params.provider.Arguments.of("anthropic",
                    "{\"id\":\"id\",\"model\":\"model\",\"content\":" + content + "}"));
        }
        return result.stream();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"openai", "anthropic"})
    void minimalTextResponseAllowsEmptyTextAndOptionalMetadata(String provider) throws Exception {
        String payload = provider.equals("openai")
                ? "{\"id\":\"id\",\"model\":\"model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}"
                : "{\"id\":\"id\",\"model\":\"model\",\"content\":[{\"type\":\"text\",\"text\":\"\"}]}";
        (provider.equals("openai") ? OPENAI_SERVER : ANTHROPIC_SERVER).enqueue(jsonResponse(200, payload));
        mockMvc.perform(post("/v1/chat/completions").contentType("application/json")
                .header("X-API-Key", "test-gateway-key").header("X-Gateway-Requested-Provider", provider)
                .content(requestBody().replace("\"model\": \"gpt-4o-mini\",", ""))).andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value(""));
    }

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start MockWebServer", exception);
        }
        return server;
    }

    private MockResponse jsonResponse(int statusCode, String body) {
        return new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String requestBody() {
        return """
                {
                  "model": "gpt-4o-mini",
                  "messages": [{"role": "user", "content": "Hello"}],
                  "max_tokens": 8
                }
                """;
    }
}
