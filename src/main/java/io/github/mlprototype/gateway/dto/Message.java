package io.github.mlprototype.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single message in a chat conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String role;
    private String content;
}
