package io.github.mlprototype.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single choice in the chat completion response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Choice {

    private int index;
    private Message message;
    private String finishReason;
}
