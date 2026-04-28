package io.github.mlprototype.gateway.provider.openai;

import io.github.mlprototype.gateway.dto.ChatRequest;
import io.github.mlprototype.gateway.dto.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiRequestMapperTest {

    private OpenAiRequestMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OpenAiRequestMapper();
        ReflectionTestUtils.setField(mapper, "defaultModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(mapper, "maxTokensLimit", 4096);
    }

    @Test
    void toOpenAiRequest_withModel_usesRequestModel() {
        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(Message.builder().role("user").content("Hello").build()))
                .build();

        Map<String, Object> result = mapper.toOpenAiRequest(request);

        assertThat(result.get("model")).isEqualTo("gpt-4o");
    }

    @Test
    void toOpenAiRequest_withoutModel_usesDefault() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder().role("user").content("Hello").build()))
                .build();

        Map<String, Object> result = mapper.toOpenAiRequest(request);

        assertThat(result.get("model")).isEqualTo("gpt-4o-mini");
    }

    @Test
    void toOpenAiRequest_enforcesMaxTokensLimit() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder().role("user").content("Hello").build()))
                .maxTokens(99999)
                .build();

        Map<String, Object> result = mapper.toOpenAiRequest(request);

        assertThat(result.get("max_tokens")).isEqualTo(4096);
    }

    @Test
    void toOpenAiRequest_withTemperature_includesIt() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder().role("user").content("Hello").build()))
                .temperature(0.7)
                .build();

        Map<String, Object> result = mapper.toOpenAiRequest(request);

        assertThat(result.get("temperature")).isEqualTo(0.7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toOpenAiRequest_mapsMessages() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        Message.builder().role("system").content("You are helpful").build(),
                        Message.builder().role("user").content("Hello").build()))
                .build();

        Map<String, Object> result = mapper.toOpenAiRequest(request);

        List<Map<String, String>> messages = (List<Map<String, String>>) result.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(1).get("content")).isEqualTo("Hello");
    }
}
