package com.spring.api.common.security;

public final class SecurityConstants {
    private SecurityConstants() {}

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String SHUTDOWN_HEADER = "X-Shutdown-Token";
    public static final String DRAIN_HEADER = "X-Drain-State";
}
