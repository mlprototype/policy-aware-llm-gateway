package io.github.mlprototype.gateway.provider.anthropic;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Anthropic provider implementation using RestClient.
 * Sends requests to Anthropic Messages API and returns normalized ChatResponse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicProvider implements LlmProvider {

    @Qualifier("anthropicRestClient")
    private final RestClient restClient;
    private final AnthropicRequestMapper requestMapper;
    private final AnthropicResponseMapper responseMapper;

    @Override
    public ProviderType getType() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        var anthropicRequest = requestMapper.toAnthropicRequest(request);

        log.debug("Sending request to Anthropic: model={}", anthropicRequest.get("model"));

        try {
            Map<String, Object> rawResponse = restClient.post()
                    .uri("/v1/messages")
                    .body(anthropicRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ProviderException(
                                ProviderType.ANTHROPIC,
                                "Anthropic client error: " + res.getStatusCode(),
                                res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProviderException(
                                ProviderType.ANTHROPIC,
                                "Anthropic server error: " + res.getStatusCode(),
                                res.getStatusCode().value());
                    })
                    .body(new ParameterizedTypeReference<>() {});

            ChatResponse response = responseMapper.toChatResponse(rawResponse);
            log.debug("Received response from Anthropic: id={}", response.getId());
            return response;

        } catch (ProviderException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Anthropic request failed: {}", e.getMessage());
            throw new ProviderException(ProviderType.ANTHROPIC, "Failed to call Anthropic: " + e.getMessage(), 502);
        }
    }
}
