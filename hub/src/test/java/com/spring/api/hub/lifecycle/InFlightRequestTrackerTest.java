package com.spring.api.hub.lifecycle;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFlightRequestTrackerTest {

    @Test
    void countsRequestsForTheWholeOfTheirLifetime() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        tracker.beginRequest();
        tracker.beginRequest();
        assertEquals(2, tracker.getInFlightCount());

        tracker.endRequest();
        assertEquals(1, tracker.getInFlightCount());

        tracker.endRequest();
        assertEquals(0, tracker.getInFlightCount());
    }

    @Test
    void acceptsTrafficUntilDrainBegins() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        assertFalse(tracker.isDraining());
        tracker.beginDrain();
        assertTrue(tracker.isDraining());
    }

    @Test
    void reportsQuiescentImmediatelyWhenIdle() {
        assertTrue(new InFlightRequestTracker().awaitQuiescence(Duration.ofSeconds(5)));
    }

    @Test
    void waitsForOutstandingRequestsToFinish() throws Exception {
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        tracker.beginRequest();

        // The in-flight request finishes while the drain is waiting on it.
        Thread releaser = new Thread(() -> {
            sleepQuietly(80);
            tracker.endRequest();
        });
        releaser.start();

        assertTrue(tracker.awaitQuiescence(Duration.ofSeconds(5)));
        releaser.join();
        assertEquals(0, tracker.getInFlightCount());
    }

    @Test
    void givesUpOnceTheDrainTimeoutElapses() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        tracker.beginRequest();

        long startedAt = System.nanoTime();
        boolean drained = tracker.awaitQuiescence(Duration.ofMillis(150));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertFalse(drained, "a stuck request must not be reported as drained");
        assertTrue(elapsedMillis >= 140, "expected to wait for the timeout, waited " + elapsedMillis + "ms");
        assertEquals(1, tracker.getInFlightCount());
    }

    @Test
    void treatsANonPositiveTimeoutAsAlreadyExpired() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        assertTrue(tracker.awaitQuiescence(Duration.ZERO));

        tracker.beginRequest();
        assertFalse(tracker.awaitQuiescence(Duration.ZERO));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}