package io.github.mlprototype.gateway.audit;

import java.util.UUID;
import java.util.regex.Pattern;

/** Correlation IDs accepted at the request boundary must fit the audit storage contract. */
public final class AuditTraceId {
    public static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1," + MAX_LENGTH + "}");

    private AuditTraceId() {}

    public static String resolve(String externalId) {
        return externalId != null && SAFE_ID.matcher(externalId).matches()
                ? externalId : UUID.randomUUID().toString();
    }
}
