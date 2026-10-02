package io.github.mlprototype.gateway.audit;

/** The request_preview VARCHAR(200) contract, including any truncation suffix. */
public final class AuditPreview {

    public static final int MAX_LENGTH = 200;
    private static final String TRUNCATION_SUFFIX = "...";

    private AuditPreview() {
    }

    public static String truncate(String preview) {
        if (preview == null || preview.length() <= MAX_LENGTH) {
            return preview;
        }
        int end = MAX_LENGTH - TRUNCATION_SUFFIX.length();
        // Do not split a UTF-16 surrogate pair at the truncation boundary.
        if (Character.isHighSurrogate(preview.charAt(end - 1))
                && Character.isLowSurrogate(preview.charAt(end))) {
            end--;
        }
        return preview.substring(0, end) + TRUNCATION_SUFFIX;
    }
}
