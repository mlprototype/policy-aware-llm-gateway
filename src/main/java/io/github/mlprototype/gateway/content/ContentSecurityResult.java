package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.ChatRequest;

/**
 * The result of evaluating content security policies.
 * Contains the final decision, the potentially modified effective request,
 * and correlation metadata.
 */
public record ContentSecurityResult(
        SecurityDecision decision,
        ChatRequest effectiveRequest,
        String sanitizedPreview,
        String requestHash
) {
}
