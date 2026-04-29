package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Orchestrates the evaluation of content security policies.
 * Executes PII and Prompt Injection detectors, then enforces actions based on tenant configuration.
 */
@Service
@RequiredArgsConstructor
public class ContentSecurityService {

    private final PiiDetector piiDetector;
    private final InjectionDetector injectionDetector;
    private final PiiMasker piiMasker;

    /**
     * Evaluates a chat request against security policies.
     * Throws SecurityBlockException if the request should be blocked.
     * Returns a ContentSecurityResult with the effective request (possibly masked) and decision metadata.
     */
    public ContentSecurityResult evaluate(ChatRequest request, PiiAction piiAction, InjectionAction injectionAction) {
        if (request == null || request.getMessages() == null) {
            return new ContentSecurityResult(
                    new SecurityDecision(new PiiDetectionResult(false, List.of()), piiAction,
                            new InjectionDetectionResult(false, List.of()), injectionAction),
                    request,
                    "",
                    "");
        }

        // 1. Execute all detections
        PiiDetectionResult piiResult = piiDetector.detect(request.getMessages());
        InjectionDetectionResult injectionResult = injectionDetector.detect(request.getMessages());

        SecurityDecision decision = new SecurityDecision(piiResult, piiAction, injectionResult, injectionAction);

        // 2. Hash and Preview generation (preview must be masked/sanitized)
        String requestHash = generateRawHash(request);
        List<Message> safeMessages = piiResult.detected() ? piiMasker.mask(request.getMessages()) : request.getMessages();
        String sanitizedPreview = generatePreview(safeMessages);

        // 3. Evaluate Actions (Block Priority: PII > INJECTION)
        if (piiResult.detected() && piiAction == PiiAction.BLOCK) {
            throw new SecurityBlockException("PII_DETECTED", decision, 400, sanitizedPreview, requestHash);
        }
        if (injectionResult.detected() && injectionAction == InjectionAction.BLOCK) {
            throw new SecurityBlockException("INJECTION_DETECTED", decision, 400, sanitizedPreview, requestHash);
        }

        // 4. Apply MASK if needed
        ChatRequest effectiveRequest = request;
        if (piiResult.detected() && piiAction == PiiAction.MASK) {
            effectiveRequest = createMaskedRequest(request, safeMessages);
        }

        return new ContentSecurityResult(decision, effectiveRequest, sanitizedPreview, requestHash);
    }

    private ChatRequest createMaskedRequest(ChatRequest original, List<Message> safeMessages) {
        return ChatRequest.builder()
                .model(original.getModel())
                .messages(safeMessages)
                .temperature(original.getTemperature())
                .maxTokens(original.getMaxTokens())
                .build();
    }

    private String generateRawHash(ChatRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Message m : request.getMessages()) {
                if (m.getContent() != null) {
                    digest.update(m.getContent().getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return "hash-failed";
        }
    }

    private String generatePreview(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m.getContent() != null) {
                sb.append(m.getContent()).append(" ");
            }
        }
        String full = sb.toString().trim();
        return full.length() > 200 ? full.substring(0, 200) + "..." : full;
    }
}
