package io.github.mlprototype.gateway.provider.anthropic;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderFailureClassifier;
import io.github.mlprototype.gateway.provider.ProviderType;
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
public class AnthropicProvider implements LlmProvider {

    private final RestClient restClient;
    private final AnthropicRequestMapper requestMapper;
    private final AnthropicResponseMapper responseMapper;
    private final ProviderFailureClassifier failureClassifier;

    public AnthropicProvider(
            @Qualifier("anthropicRestClient") RestClient restClient,
            AnthropicRequestMapper requestMapper,
            AnthropicResponseMapper responseMapper,
            ProviderFailureClassifier failureClassifier) {
        this.restClient = restClient;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.failureClassifier = failureClassifier;
    }

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
                        throw failureClassifier.upstream4xx(ProviderType.ANTHROPIC, res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw failureClassifier.upstream5xx(ProviderType.ANTHROPIC, res.getStatusCode().value());
                    })
                    .body(new ParameterizedTypeReference<>() {});

            if (rawResponse == null) {
                throw failureClassifier.invalidResponse(ProviderType.ANTHROPIC, "Empty response from anthropic", null);
            }

            ChatResponse response = responseMapper.toChatResponse(rawResponse);
            log.debug("Received response from Anthropic: id={}", response.getId());
            return response;

        } catch (ProviderException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Anthropic request failed: {}", e.getMessage());
            throw failureClassifier.classifyClientException(ProviderType.ANTHROPIC, e);
        } catch (RuntimeException e) {
            throw failureClassifier.invalidResponse(ProviderType.ANTHROPIC, "Invalid response from anthropic", e);
        }
    }
}
