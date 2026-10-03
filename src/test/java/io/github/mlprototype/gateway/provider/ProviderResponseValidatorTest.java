package io.github.mlprototype.gateway.provider;

import io.github.mlprototype.gateway.dto.*;
import io.github.mlprototype.gateway.exception.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class ProviderResponseValidatorTest {
    @ParameterizedTest
    @EnumSource(ProviderType.class)
    void rejectsMissingRequiredFieldsAsInvalidWithoutFallback(ProviderType provider) {
        for (ChatResponse response : Arrays.asList(null, ChatResponse.builder().build(),
                response(null, "model"), response(" ", "model"), response("id", null), response("id", " "),
                ChatResponse.builder().id("id").model("model").choices(List.of()).build())) {
            assertThatThrownBy(() -> ProviderResponseValidator.validate(response, provider))
                    .isInstanceOfSatisfying(ProviderException.class, ex -> {
                        assertThat(ex.getFailureType()).isEqualTo(ProviderFailureType.INVALID_RESPONSE);
                        assertThat(ex.isFallbackEligible()).isFalse();
                    });
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderType.class)
    void optionalMetadataAndEmptyTextRemainValid(ProviderType provider) {
        assertThat(ProviderResponseValidator.validate(response("id", "model"), provider)).isNotNull();
        var longModel = response("id", "m".repeat(101));
        assertThat(ProviderResponseValidator.validate(longModel, provider)).isSameAs(longModel);
        assertThat(longModel.getModel()).hasSize(101);
    }

    private ChatResponse response(String id, String model) {
        return ChatResponse.builder().id(id).model(model).choices(List.of(
                Choice.builder().message(new Message("assistant", "")).build())).build();
    }
}
