package io.github.mlprototype.gateway.provider.openai;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Gateway ChatRequest to OpenAI Chat Completions API format.
 * Isolates OpenAI-specific field naming and defaults.
 */
@Component
public class OpenAiRequestMapper {

    @Value("${gateway.provider.openai.default-model:gpt-4o-mini}")
    private String defaultModel;

    @Value("${gateway.provider.openai.max-tokens-limit:4096}")
    private int maxTokensLimit;

    /**
     * Converts a ChatRequest into the Map payload expected by OpenAI.
     * Applies default model and enforces max_tokens limit.
     */
    public Map<String, Object> toOpenAiRequest(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();

        // Model: use request value or fall back to default
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);

        // Messages
        List<Map<String, String>> messages = request.getMessages().stream()
                .map(this::messageToMap)
                .toList();
        body.put("messages", messages);

        // Temperature
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }

        // Max tokens: enforce ceiling for cost safety
        int maxTokens = request.getMaxTokens() != null
                ? Math.min(request.getMaxTokens(), maxTokensLimit)
                : maxTokensLimit;
        body.put("max_tokens", maxTokens);

        return body;
    }

    private Map<String, String> messageToMap(Message message) {
        Map<String, String> map = new HashMap<>();
        map.put("role", message.getRole());
        map.put("content", message.getContent());
        return map;
    }
}
