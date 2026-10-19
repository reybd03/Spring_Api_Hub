package com.spring.api.hub.lifecycle;

/**
 * Removes this instance from whatever registry routes traffic to it.
 *
 * <p>The primary mechanism is the readiness signal published by
 * {@link GracefulShutdownCoordinator}: a gateway or orchestrator that polls
 * {@code /actuator/health/readiness} stops sending new work the moment the instance reports
 * {@code OUT_OF_SERVICE}. Implementations of this interface are the belt-and-braces path for
 * registries that need an explicit deregistration call, and every registered implementation is
 * invoked on drain.
 */
public interface ServiceDeregistration {

    /**
     * @param reason human readable cause, useful for audit logs
     */
    void deregister(String reason);
}