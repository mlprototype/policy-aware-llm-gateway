package io.github.mlprototype.gateway.api;

public final class GatewayHeaders {

    public static final String PROVIDER_HEADER = "X-Gateway-Provider";
    public static final String REQUESTED_PROVIDER_HEADER = "X-Gateway-Requested-Provider";
    public static final String FALLBACK_USED_HEADER = "X-Gateway-Fallback-Used";

    private GatewayHeaders() {
    }
}
