package com.spring.api.hub.lifecycle;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls how this service sheds traffic before it exits.
 *
 * <p>These values sit alongside, rather than replace, Spring Boot's own
 * {@code server.shutdown} and {@code spring.lifecycle.timeout-per-shutdown-phase}: those
 * govern the transport level drain, while these govern how long the platform waits for
 * in-flight work to settle before it gives up and lets the process die.
 *
 * @param drainTimeout how long to wait for in-flight requests to finish once draining starts
 * @param shutdownToken shared secret required to call {@code POST /actuator/shutdown}; when
 *        blank the endpoint refuses every caller rather than running unauthenticated
 */
@ConfigurationProperties(prefix = "platform.shutdown")
public record ShutdownProperties(Duration drainTimeout, String shutdownToken) {

    /** Header carrying the shared secret that authorises {@code POST /actuator/shutdown}. */
    public static final String TOKEN_HEADER = "X-Shutdown-Token";

    /** Used when {@code platform.shutdown.drain-timeout} is not configured. */
    public static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(20);

    public ShutdownProperties {
        if (drainTimeout == null || drainTimeout.isNegative()) {
            drainTimeout = DEFAULT_DRAIN_TIMEOUT;
        }
        if (shutdownToken == null) {
            shutdownToken = "";
        }
    }

    /**
     * A shutdown can only be authorised when a secret has actually been provisioned.
     */
    public boolean hasToken() {
        return !shutdownToken.isBlank();
    }
}