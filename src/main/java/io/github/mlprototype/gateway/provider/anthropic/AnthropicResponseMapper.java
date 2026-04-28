package io.github.mlprototype.gateway.provider.anthropic;

import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.dto.Choice;
import io.github.mlprototype.gateway.dto.Message;
import io.github.mlprototype.gateway.dto.Usage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Maps Anthropic Messages API response to Gateway's normalized ChatResponse.
 * Anthropic response differences:
 *   - content is an array of blocks [{type:"text", text:"..."}]
 *   - usage fields: input_tokens, output_tokens (not prompt_tokens, completion_tokens)
 *   - no 'created' timestamp in response
 */
@Component
public class AnthropicResponseMapper {

    /**
     * Converts an Anthropic API response (as Map) to a normalized ChatResponse.
     */
    @SuppressWarnings("unchecked")
    public ChatResponse toChatResponse(Map<String, Object> anthropicResponse) {
        // Extract text from content blocks
        String text = "";
        List<Map<String, Object>> contentBlocks =
                (List<Map<String, Object>>) anthropicResponse.get("content");
        if (contentBlocks != null && !contentBlocks.isEmpty()) {
            // Use first text block
            for (Map<String, Object> block : contentBlocks) {
                if ("text".equals(block.get("type"))) {
                    text = (String) block.get("text");
                    break;
                }
            }
        }

        // Build usage
        Usage usage = null;
        Map<String, Object> usageMap = (Map<String, Object>) anthropicResponse.get("usage");
        if (usageMap != null) {
            int inputTokens = toInt(usageMap.get("input_tokens"));
            int outputTokens = toInt(usageMap.get("output_tokens"));
            usage = Usage.builder()
                    .promptTokens(inputTokens)
                    .completionTokens(outputTokens)
                    .totalTokens(inputTokens + outputTokens)
                    .build();
        }

        return ChatResponse.builder()
                .id((String) anthropicResponse.get("id"))
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model((String) anthropicResponse.get("model"))
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(Message.builder()
                                        .role("assistant")
                                        .content(text)
                                        .build())
                                .finishReason(mapStopReason(
                                        (String) anthropicResponse.get("stop_reason")))
                                .build()
                ))
                .usage(usage)
                .build();
    }

    /**
     * Maps Anthropic stop_reason to OpenAI-compatible finish_reason.
     */
    private String mapStopReason(String stopReason) {
        if (stopReason == null) return null;
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            default -> stopReason;
        };
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
