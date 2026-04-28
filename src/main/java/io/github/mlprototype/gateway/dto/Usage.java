package io.github.mlprototype.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token usage statistics from the LLM provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usage {

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
}
