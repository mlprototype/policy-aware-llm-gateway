package io.github.mlprototype.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts a bounded, sanitized message from provider error responses.
 */
@Component
@RequiredArgsConstructor
public class ProviderErrorDetailExtractor {

    private static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_DETAIL_LENGTH = 500;

    private final ObjectMapper objectMapper;

    public String extract(ClientHttpResponse response) {
        try {
            byte[] body = response.getBody().readNBytes(MAX_BODY_BYTES);
            if (body.length == 0) {
                return null;
            }

            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode error = root.path("error");
            String detail = error.isObject()
                    ? error.path("message").asText(null)
                    : error.asText(null);
            if (detail == null || detail.isBlank()) {
                detail = root.path("message").asText(null);
            }
            return sanitize(detail);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private String sanitize(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String sanitized = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > MAX_DETAIL_LENGTH
                ? sanitized.substring(0, MAX_DETAIL_LENGTH) + "..."
                : sanitized;
    }
}
