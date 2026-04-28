package io.github.mlprototype.gateway.exception;

import io.github.mlprototype.gateway.api.GatewayHeaders;
import io.github.mlprototype.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for all Gateway endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ProviderRoutingException.class)
    public ResponseEntity<ErrorResponse> handleProviderRoutingException(ProviderRoutingException ex) {
        log.error("Provider routing error [requested={}, resolved={}]: {}",
                ex.getRequestedProvider(), ex.getResolvedProvider(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(ex.getStatusCode())
                .error(httpError(ex.getStatusCode()))
                .message(ex.getMessage())
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        HttpHeaders headers = new HttpHeaders();
        if (ex.getRequestedProvider() != null) {
            headers.add(GatewayHeaders.REQUESTED_PROVIDER_HEADER, ex.getRequestedProvider().getValue());
        }
        if (ex.getResolvedProvider() != null) {
            headers.add(GatewayHeaders.PROVIDER_HEADER, ex.getResolvedProvider().getValue());
        }
        headers.add(GatewayHeaders.FALLBACK_USED_HEADER, String.valueOf(ex.isFallbackUsed()));

        return ResponseEntity.status(ex.getStatusCode()).headers(headers).body(body);
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ErrorResponse> handleProviderException(ProviderException ex) {
        log.error("Provider error [{} / {}]: {}",
                ex.getProviderType(), ex.getFailureType(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(ex.getStatusCode())
                .error(httpError(ex.getStatusCode()))
                .message(ex.getMessage())
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(GatewayHeaders.PROVIDER_HEADER, ex.getProviderType().getValue());
        headers.add(GatewayHeaders.FALLBACK_USED_HEADER, "false");
        return ResponseEntity.status(ex.getStatusCode()).headers(headers).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("Validation error: {}", message);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorResponse> handleGatewayException(GatewayException ex) {
        log.error("Gateway error: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(ex.getStatusCode())
                .error(httpError(ex.getStatusCode()))
                .message(ex.getMessage())
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .traceId(MDC.get(TRACE_ID_KEY))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String httpError(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 429 -> "Too Many Requests";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Gateway Error";
        };
    }
}
