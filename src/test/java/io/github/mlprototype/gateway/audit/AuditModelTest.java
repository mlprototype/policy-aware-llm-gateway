package io.github.mlprototype.gateway.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class AuditModelTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"gpt-4o-mini"})
    void preservesShortOrAbsentModel(String model) {
        assertThat(AuditModel.truncate(model)).isEqualTo(model);
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 100, 101, 1000})
    void boundsTheStoredPrefixWithoutSuffix(int length) {
        String model = "m".repeat(length);
        assertThat(AuditModel.truncate(model)).isEqualTo("m".repeat(Math.min(length, 100)));
    }

    @Test
    void doesNotSplitSurrogatePairAtStorageBoundary() {
        assertThat(AuditModel.truncate("m".repeat(99) + "😀tail")).isEqualTo("m".repeat(99));
    }
}
