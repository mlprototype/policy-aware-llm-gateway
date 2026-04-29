package io.github.mlprototype.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.dto.Choice;
import io.github.mlprototype.gateway.dto.Message;
import io.github.mlprototype.gateway.dto.Usage;
import io.github.mlprototype.gateway.exception.ProviderFailureType;
import io.github.mlprototype.gateway.exception.ProviderRoutingException;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.router.FallbackReason;
import io.github.mlprototype.gateway.router.ProviderExecutionResult;
import io.github.mlprototype.gateway.router.ProviderRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatCompletionController.class)
class ChatCompletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderRoutingService providerRoutingService;

    @MockitoBean
    private AuditLogger auditLogger;

    @MockitoBean
    private io.github.mlprototype.gateway.security.AuthenticationService authenticationService;

    @MockitoBean
    private io.github.mlprototype.gateway.content.ContentSecurityService contentSecurityService;

    @MockitoBean
    private io.github.mlprototype.gateway.ratelimit.RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
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

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
    }

    @Test
    void createChatCompletion_withValidRequest_returns200AndGatewayHeaders() throws Exception {
        when(providerRoutingService.execute(any(), any(), any()))
                .thenReturn(ProviderExecutionResult.builder()
                        .requestedProvider(ProviderType.OPENAI)
                        .resolvedProvider(ProviderType.ANTHROPIC)
                        .fallbackUsed(true)
                        .fallbackReason(FallbackReason.PRIMARY_TIMEOUT)
                        .response(createResponse())
                        .build());

        String requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Hello"}
                    ]
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-gateway-key")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai"))
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "anthropic"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "true"))
                .andExpect(jsonPath("$.id").value("chatcmpl-test"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.choices[0].message.content").value("Hello!"));
    }

    @Test
    void createChatCompletion_withRoutingFailure_returns503AndHeaders() throws Exception {
        when(providerRoutingService.execute(any(), any(), any()))
                .thenThrow(new ProviderRoutingException(
                        "Fallback provider unavailable",
                        503,
                        ProviderType.OPENAI,
                        ProviderType.ANTHROPIC,
                        true,
                        FallbackReason.PRIMARY_TIMEOUT,
                        ProviderFailureType.TIMEOUT,
                        ProviderFailureType.BREAKER_OPEN,
                        null));

        String requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Hello"}
                    ]
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-gateway-key")
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(GatewayHeaders.REQUESTED_PROVIDER_HEADER, "openai"))
                .andExpect(header().string(GatewayHeaders.PROVIDER_HEADER, "anthropic"))
                .andExpect(header().string(GatewayHeaders.FALLBACK_USED_HEADER, "true"));
    }

    @Test
    void createChatCompletion_withEmptyMessages_returns400() throws Exception {
        String requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": []
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-gateway-key")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChatCompletion_withoutApiKey_returns401() throws Exception {
        String requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {"role": "user", "content": "Hello"}
                    ]
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private ChatResponse createResponse() {
        return ChatResponse.builder()
                .id("chatcmpl-test")
                .object("chat.completion")
                .created(System.currentTimeMillis() / 1000)
                .model("gpt-4o-mini")
                .choices(List.of(Choice.builder()
                        .index(0)
                        .message(Message.builder()
                                .role("assistant")
                                .content("Hello!")
                                .build())
                        .finishReason("stop")
                        .build()))
                .usage(Usage.builder()
                        .promptTokens(5)
                        .completionTokens(2)
                        .totalTokens(7)
                        .build())
                .build();
    }
}
