package io.github.mlprototype.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @JsonProperty("prompt_tokens")
    @Schema(description = "入力に使用されたトークン数。", example = "24")
    private int promptTokens;

    @JsonProperty("completion_tokens")
    @Schema(description = "出力に使用されたトークン数。", example = "12")
    private int completionTokens;

    @JsonProperty("total_tokens")
    @Schema(description = "入力と出力の合計トークン数。", example = "36")
    private int totalTokens;
}
