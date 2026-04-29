package io.github.mlprototype.gateway.provider.openai;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderFailureClassifier;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI provider implementation using RestClient.
 * Sends requests to OpenAI Chat Completions API and returns normalized responses.
 */
@Slf4j
@Component
public class OpenAiProvider implements LlmProvider {

    private final RestClient restClient;
    private final OpenAiRequestMapper requestMapper;
    private final ProviderFailureClassifier failureClassifier;

    public OpenAiProvider(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiRequestMapper requestMapper,
            ProviderFailureClassifier failureClassifier) {
        this.restClient = restClient;
        this.requestMapper = requestMapper;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public ProviderType getType() {
        return ProviderType.OPENAI;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        var openAiRequest = requestMapper.toOpenAiRequest(request);

        log.debug("Sending request to OpenAI: model={}", openAiRequest.get("model"));

        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(openAiRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw failureClassifier.upstream4xx(ProviderType.OPENAI, res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw failureClassifier.upstream5xx(ProviderType.OPENAI, res.getStatusCode().value());
                    })
                    .body(ChatResponse.class);

            if (response == null) {
                throw failureClassifier.invalidResponse(ProviderType.OPENAI, "Empty response from openai", null);
            }

            log.debug("Received response from OpenAI: id={}", response != null ? response.getId() : "null");
            return response;

        } catch (ProviderException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("OpenAI request failed: {}", e.getMessage());
            throw failureClassifier.classifyClientException(ProviderType.OPENAI, e);
        } catch (RuntimeException e) {
            throw failureClassifier.invalidResponse(ProviderType.OPENAI, "Invalid response from openai", e);
        }
    }
}
