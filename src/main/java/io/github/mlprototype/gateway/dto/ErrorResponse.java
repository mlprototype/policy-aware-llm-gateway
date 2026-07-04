package io.github.mlprototype.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized error response returned by the Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @Schema(description = "HTTP ステータスコード。", example = "400")
    private int status;

    @Schema(description = "HTTP エラー種別。", example = "Bad Request")
    private String error;

    @Schema(description = "エラー内容。", example = "Request could not be processed")
    private String message;

    @JsonProperty("trace_id")
    @Schema(description = "Gateway が付与したトレース ID。", example = "021cb519-6cf2-4c65-8e15-c94a1446651a")
    private String traceId;

    @Builder.Default
    @Schema(description = "エラー発生時刻の epoch milliseconds。", example = "1778512027354")
    private long timestamp = Instant.now().toEpochMilli();
}
