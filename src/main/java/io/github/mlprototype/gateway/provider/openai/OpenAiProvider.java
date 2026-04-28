package io.github.mlprototype.gateway.provider.openai;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.ChatResponse;
import io.github.mlprototype.gateway.exception.ProviderException;
import io.github.mlprototype.gateway.provider.LlmProvider;
import io.github.mlprototype.gateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OpenAiProvider implements LlmProvider {

    @Qualifier("openAiRestClient")
    private final RestClient restClient;
    private final OpenAiRequestMapper requestMapper;

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
                        throw new ProviderException(
                                ProviderType.OPENAI,
                                "OpenAI client error: " + res.getStatusCode(),
                                res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProviderException(
                                ProviderType.OPENAI,
                                "OpenAI server error: " + res.getStatusCode(),
                                res.getStatusCode().value());
                    })
                    .body(ChatResponse.class);

            log.debug("Received response from OpenAI: id={}", response != null ? response.getId() : "null");
            return response;

        } catch (ProviderException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("OpenAI request failed: {}", e.getMessage());
            throw new ProviderException(ProviderType.OPENAI, "Failed to call OpenAI: " + e.getMessage(), 502);
        }
    }
}
