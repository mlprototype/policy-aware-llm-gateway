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
                                    new io.github.mlprototype.gateway.content.InjectionDetectionResult(false, java.util.List.of()),
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
                  "model": "claude-3-haiku-20240307",
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
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "false"));

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
                  "model": "claude-3-haiku-20240307",
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
