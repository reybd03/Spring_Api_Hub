package com.spring.api.hub.lifecycle;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Counts the requests this instance is currently serving so a shutdown can wait for real work
 * instead of sleeping for a fixed period and hoping.
 *
 * <p>Only business traffic is tracked. Management endpoints (health, probes, shutdown) are
 * deliberately excluded so the drain can still be observed and driven while it runs.
 */
@Component
public class InFlightRequestTracker {

    private static final long POLL_INTERVAL_MILLIS = 25L;

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean draining;

    /**
     * Marks the start of a business request. Must be paired with {@link #endRequest()} in a
     * {@code doFinally} so cancellations still decrement the counter.
     */
    public void beginRequest() {
        inFlight.incrementAndGet();
    }

    public void endRequest() {
        inFlight.decrementAndGet();
    }

    public int getInFlightCount() {
        return inFlight.get();
    }

    public boolean isDraining() {
        return draining;
    }

    /**
     * Flips the instance into draining mode: new business requests are refused, existing ones
     * keep running.
     */
    public void beginDrain() {
        draining = true;
    }

    /**
     * Blocks until every tracked request has finished.
     *
     * @return {@code true} when the instance went quiet within the timeout, {@code false} when
     *         requests were still running and the caller should expect truncated responses
     */
    public boolean awaitQuiescence(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0) {
            if (System.nanoTime() - deadline >= 0) {
                return false;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}