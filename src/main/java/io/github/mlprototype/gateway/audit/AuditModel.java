package io.github.mlprototype.gateway.audit;

/** The audit model VARCHAR(100) contract; provider response values remain unchanged. */
public final class AuditModel {
    public static final int MAX_LENGTH = 100;

    private AuditModel() {}

    public static String truncate(String model) {
        if (model == null || model.length() <= MAX_LENGTH) {
            return model;
        }
        int end = MAX_LENGTH;
        if (Character.isHighSurrogate(model.charAt(end - 1))
                && Character.isLowSurrogate(model.charAt(end))) {
            end--;
        }
        return model.substring(0, end);
    }
}
