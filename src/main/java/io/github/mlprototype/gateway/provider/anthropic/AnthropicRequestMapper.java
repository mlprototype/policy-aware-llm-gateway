package io.github.mlprototype.gateway.provider.anthropic;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Gateway ChatRequest to Anthropic Messages API format.
 * Key differences from OpenAI:
 *   - system prompt is a top-level field, not in messages array
 *   - messages only contain role=user and role=assistant
 *   - max_tokens is required
 */
@Component
public class AnthropicRequestMapper {

    @Value("${gateway.provider.anthropic.default-model:claude-3-haiku-20240307}")
    private String defaultModel;

    @Value("${gateway.provider.anthropic.max-tokens-limit:4096}")
    private int maxTokensLimit;

    /**
     * Converts a ChatRequest into the Map payload expected by Anthropic Messages API.
     */
    public Map<String, Object> toAnthropicRequest(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();

        // Model
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);

        // Extract system messages and separate from user/assistant messages
        String systemPrompt = null;
        List<Map<String, String>> messages = new ArrayList<>();

        for (Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                // Anthropic: system is a top-level field
                systemPrompt = (systemPrompt == null)
                        ? msg.getContent()
                        : systemPrompt + "\n" + msg.getContent();
            } else {
                Map<String, String> map = new HashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                messages.add(map);
            }
        }

        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }
        body.put("messages", messages);

        // Max tokens: required for Anthropic, enforce ceiling
        int maxTokens = request.getMaxTokens() != null
                ? Math.min(request.getMaxTokens(), maxTokensLimit)
                : maxTokensLimit;
        body.put("max_tokens", maxTokens);

        // Temperature
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }

        return body;
    }
}
