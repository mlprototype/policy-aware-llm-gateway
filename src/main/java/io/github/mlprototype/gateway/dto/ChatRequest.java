package io.github.mlprototype.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.mlprototype.gateway.audit.AuditModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @Schema(description = "利用するモデル名。省略時はプロバイダごとのデフォルトモデルを使用します。", example = "gpt-4o-mini")
    @Size(max = AuditModel.MAX_LENGTH, message = "model must not exceed 100 characters")
    private String model;

    @NotNull(message = "messages must not be null")
    @NotEmpty(message = "messages must not be empty")
    @Schema(description = "チャットメッセージ一覧。content には日本語をそのまま指定できます。")
    private List<@NotNull(message = "message must not be null") @Valid Message> messages;

    @Schema(description = "出力のランダム性を調整する値。", example = "0.7")
    private Double temperature;

    @JsonProperty("max_tokens")
    @Positive(message = "max_tokens must be positive")
    @Schema(description = "生成時の最大トークン数。Gateway 側で上限値にクランプされます。", example = "1024")
    private Integer maxTokens;

    // Sprint 2+: stream, tools, response_format
}
