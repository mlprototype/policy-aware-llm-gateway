package io.github.mlprototype.gateway.api;

public final class GatewayHeaders {

    public static final String PROVIDER_HEADER = "X-Gateway-Provider";
    public static final String REQUESTED_PROVIDER_HEADER = "X-Gateway-Requested-Provider";
    public static final String FALLBACK_USED_HEADER = "X-Gateway-Fallback-Used";
    public static final String SECURITY_BLOCKED_HEADER = "X-Gateway-Security-Blocked";
    public static final String BLOCK_REASON_HEADER = "X-Gateway-Block-Reason";
    public static final String SECURITY_SCORE_HEADER = "X-Gateway-Security-Score";
    public static final String SECURITY_CATEGORIES_HEADER = "X-Gateway-Security-Categories";

    private GatewayHeaders() {
    }
}
