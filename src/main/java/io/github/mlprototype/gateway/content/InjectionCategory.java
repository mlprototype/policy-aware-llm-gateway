package io.github.mlprototype.gateway.content;

/**
 * High-level categories used to classify prompt injection signals.
 */
public enum InjectionCategory {
    INSTRUCTION_OVERRIDE,
    SYSTEM_PROMPT_EXTRACTION,
    POLICY_BYPASS,
    ROLE_MANIPULATION,
    JAILBREAK,
    SECRET_EXFILTRATION,
    OBFUSCATION
}
