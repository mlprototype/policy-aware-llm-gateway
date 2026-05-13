package io.github.mlprototype.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "メッセージの役割。", allowableValues = {"system", "user", "assistant"}, example = "user")
    private String role;

    @Schema(description = "メッセージ本文。日本語をそのまま指定できます。", example = "こんにちは。日本語で短く自己紹介してください。")
    private String content;
}
