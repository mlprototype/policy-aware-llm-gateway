package io.github.mlprototype.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderErrorDetailExtractorTest {

    private final ProviderErrorDetailExtractor extractor =
            new ProviderErrorDetailExtractor(new ObjectMapper());

    @Test
    void extractReadsNestedAnthropicErrorMessage() throws Exception {
        ClientHttpResponse response = response("""
                {"type":"error","error":{"type":"invalid_request_error","message":"model: invalid model ID"}}
                """);

        assertThat(extractor.extract(response)).isEqualTo("model: invalid model ID");
    }

    @Test
    void extractReadsOpenAiErrorMessageAndRemovesLineBreaks() throws Exception {
        ClientHttpResponse response = response("""
                {"error":{"message":"invalid request\\ncheck model"}}
                """);

        assertThat(extractor.extract(response)).isEqualTo("invalid request check model");
    }

    private ClientHttpResponse response(String body) throws Exception {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getBody()).thenReturn(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
