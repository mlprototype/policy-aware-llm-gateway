package io.github.mlprototype.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.mlprototype.gateway.audit.AuditLogger;
import io.github.mlprototype.gateway.dto.*;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import io.github.mlprototype.gateway.router.ProviderRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatCompletionController.class)
class ChatCompletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderRouter providerRouter;

    @MockitoBean
    private AuditLogger auditLogger;

    @MockitoBean
    private io.github.mlprototype.gateway.security.AuthenticationService authenticationService;

    @MockitoBean
    private io.github.mlprototype.gateway.ratelimit.RateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Mock AuthenticationService for ApiKeyFilter
        when(authenticationService.authenticate("test-gateway-key"))
                .thenReturn(new io.github.mlprototype.gateway.security.RequestContext("tenant-test", "client-test", 60));
        when(authenticationService.authenticate(org.mockito.ArgumentMatchers.argThat(arg -> !"test-gateway-key".equals(arg))))
                .thenThrow(new io.github.mlprototype.gateway.exception.GatewayException("Invalid or missing API key", 401));

        // Mock RateLimiter for RateLimitFilter
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
    void createChatCompletion_withValidRequest_returns200() throws Exception {
        LlmProvider mockProvider = createMockProvider();
        when(providerRouter.resolve(any())).thenReturn(mockProvider);

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
                .andExpect(jsonPath("$.id").value("chatcmpl-test"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.choices[0].message.content").value("Hello!"));
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

    private LlmProvider createMockProvider() {
        return new LlmProvider() {
            @Override
            public ProviderType getType() {
                return ProviderType.OPENAI;
            }

            @Override
            public ChatResponse complete(ChatRequest request) {
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
        };
    }
}
