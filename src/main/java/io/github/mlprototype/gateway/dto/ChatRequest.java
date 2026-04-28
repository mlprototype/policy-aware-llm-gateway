package io.github.mlprototype.gateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI-compatible chat completion request.
 * Clients send this format regardless of the target provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String model;

    @NotNull(message = "messages must not be null")
    @NotEmpty(message = "messages must not be empty")
    private List<Message> messages;

    private Double temperature;

    private Integer maxTokens;

    // Sprint 2+: stream, tools, response_format
}
