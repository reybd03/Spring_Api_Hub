package com.spring.api.hub.lifecycle;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * Actuator endpoint exposing graceful shutdown status for monitoring and UI polling.
 * Available at {@code GET /actuator/shutdown-status}.
 */
@Component
@Endpoint(id = "shutdown-status")
public class ShutdownStatusEndpoint {

    private final GracefulShutdownCoordinator coordinator;
    private final InFlightRequestTracker requestTracker;
    private final ShutdownProperties properties;

    public ShutdownStatusEndpoint(GracefulShutdownCoordinator coordinator,
                                  InFlightRequestTracker requestTracker,
                                  ShutdownProperties properties) {
        this.coordinator = coordinator;
        this.requestTracker = requestTracker;
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> status() {
        boolean draining = coordinator.isDraining() || requestTracker.isDraining();
        boolean complete = coordinator.isComplete();
        int inFlight = requestTracker.getInFlightCount();
        long drainTimeoutMs = properties.drainTimeout().toMillis();

        // Calculate progress estimate
        int progress = calculateProgress(draining, complete, inFlight);

        return Map.of(
                "draining", draining,
                "complete", complete,
                "inFlightRequests", inFlight,
                "drainTimeoutMs", drainTimeoutMs,
                "progressPercent", progress,
                "tokenConfigured", properties.hasToken(),
                "timestamp", Instant.now().toString()
        );
    }

    private int calculateProgress(boolean draining, boolean complete, int inFlight) {
        if (complete) return 100;
        if (!draining) return 0;
        if (inFlight == 0) return 95;

        // We don't track start time here, so estimate based on in-flight count
        // A more accurate implementation would track drain start time
        return Math.min(90, 10 + (inFlight * 5)); // Rough heuristic
    }
}