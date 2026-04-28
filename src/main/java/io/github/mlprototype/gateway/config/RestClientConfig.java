package io.github.mlprototype.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient configuration for LLM provider HTTP calls.
 * Each provider gets its own RestClient bean with base URL, auth, and timeout.
 */
@Configuration
public class RestClientConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(
            @Value("${gateway.provider.openai.base-url}") String baseUrl,
            @Value("${gateway.provider.openai.api-key}") String apiKey,
            @Value("${gateway.provider.openai.timeout-seconds:30}") int timeoutSeconds) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(clientHttpRequestFactory(timeoutSeconds))
                .build();
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory(int timeoutSeconds) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }

    @Bean("anthropicRestClient")
    public RestClient anthropicRestClient(
            @Value("${gateway.provider.anthropic.base-url}") String baseUrl,
            @Value("${gateway.provider.anthropic.api-key}") String apiKey,
            @Value("${gateway.provider.anthropic.api-version:2023-06-01}") String apiVersion,
            @Value("${gateway.provider.anthropic.timeout-seconds:30}") int timeoutSeconds) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", apiVersion)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(clientHttpRequestFactory(timeoutSeconds))
                .build();
    }
}
