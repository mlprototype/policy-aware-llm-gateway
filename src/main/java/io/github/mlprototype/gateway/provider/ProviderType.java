package io.github.mlprototype.gateway.provider;

/**
 * Enumeration of supported LLM providers.
 * ANTHROPIC is defined but not implemented until Sprint 2.
 */
public enum ProviderType {

    OPENAI("openai"),
    ANTHROPIC("anthropic");  // Reserved for Sprint 2

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProviderType fromValue(String value) {
        for (ProviderType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }
}
