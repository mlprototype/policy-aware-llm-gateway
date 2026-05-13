package io.github.mlprototype.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "レスポンス候補の index。", example = "0")
    private int index;

    @Schema(description = "生成された assistant メッセージ。")
    private Message message;

    @JsonProperty("finish_reason")
    @Schema(description = "生成終了理由。", example = "stop")
    private String finishReason;
}
