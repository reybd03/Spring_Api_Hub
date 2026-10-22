package com.spring.api.hub.lifecycle;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShutdownStatusEndpointTest {

    private GracefulShutdownCoordinator coordinator;
    private InFlightRequestTracker requestTracker;
    private ShutdownStatusEndpoint endpoint;

    @BeforeEach
    void setUp() {
        coordinator = mock(GracefulShutdownCoordinator.class);
        requestTracker = mock(InFlightRequestTracker.class);
        endpoint = new ShutdownStatusEndpoint(coordinator, requestTracker,
                new ShutdownProperties(Duration.ofSeconds(20), "test-token"));
    }

    @Test
    void reportsNotStartedBeforeAnyDrain() {
        when(coordinator.isDraining()).thenReturn(false);
        when(coordinator.isComplete()).thenReturn(false);
        when(requestTracker.isDraining()).thenReturn(false);
        when(requestTracker.getInFlightCount()).thenReturn(0);

        Map<String, Object> status = endpoint.status();

        assertEquals(false, status.get("draining"));
        assertEquals(false, status.get("complete"));
        assertEquals(0, status.get("progressPercent"));
        assertEquals(20000L, status.get("drainTimeoutMs"));
        assertEquals(true, status.get("tokenConfigured"));
    }

    @Test
    void reportsDrainingWithInFlightRequests() {
        when(coordinator.isDraining()).thenReturn(true);
        when(requestTracker.getInFlightCount()).thenReturn(2);

        Map<String, Object> status = endpoint.status();

        assertEquals(true, status.get("draining"));
        assertEquals(false, status.get("complete"));
        assertEquals(2, status.get("inFlightRequests"));
        assertEquals(20, status.get("progressPercent"));
    }

    @Test
    void reportsCompletionOnlyWhenCoordinatorSaysSo() {
        when(coordinator.isDraining()).thenReturn(false);
        when(coordinator.isComplete()).thenReturn(true);
        when(requestTracker.getInFlightCount()).thenReturn(0);

        Map<String, Object> status = endpoint.status();

        assertEquals(false, status.get("draining"));
        assertEquals(true, status.get("complete"));
        assertEquals(100, status.get("progressPercent"));
    }

    @Test
    void zeroInFlightWhileDrainingIsNotYetComplete() {
        when(coordinator.isDraining()).thenReturn(true);
        when(coordinator.isComplete()).thenReturn(false);
        when(requestTracker.getInFlightCount()).thenReturn(0);

        Map<String, Object> status = endpoint.status();

        assertEquals(true, status.get("draining"));
        assertEquals(false, status.get("complete"));
        assertEquals(95, status.get("progressPercent"));
    }
}
