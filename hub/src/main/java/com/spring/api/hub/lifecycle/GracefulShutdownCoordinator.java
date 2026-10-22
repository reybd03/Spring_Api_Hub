package com.spring.api.hub.lifecycle;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Sequences the shutdown so traffic stops arriving before the process starts tearing itself
 * down, and so the instance only dies once outstanding work has finished.
 *
 * <p>The steps run in a deliberate order. Readiness flips first because it is the only signal
 * every caller in the platform honours, including orchestrators that never see this JVM's
 * sockets. Only then do we refuse new local requests and end the streaming feeds, because
 * those only matter once nothing new is being routed here. Deregistration follows, and the wait
 * for in-flight work comes last since everything above it exists to shorten that wait.
 *
 * <p>This runs from {@link ContextClosedEvent}, which Spring publishes before it stops the
 * lifecycle beans and before it destroys any of them. That ordering is what lets us drain
 * while the datasource, the repositories and the HTTP server are all still usable.
 */
@Component
public class GracefulShutdownCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    private final ApplicationContext applicationContext;
    private final InFlightRequestTracker requestTracker;
    private final DrainSignal drainSignal;
    private final MessageBufferRegistry messageBuffers;
    private final List<ServiceDeregistration> deregistrations;
    private final ShutdownProperties properties;
    private final AtomicBoolean drained = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    public GracefulShutdownCoordinator(ApplicationContext applicationContext, InFlightRequestTracker requestTracker,
            DrainSignal drainSignal, MessageBufferRegistry messageBuffers,
            ObjectProvider<ServiceDeregistration> deregistrations, ShutdownProperties properties) {
        this.applicationContext = applicationContext;
        this.requestTracker = requestTracker;
        this.drainSignal = drainSignal;
        this.messageBuffers = messageBuffers;
        this.deregistrations = deregistrations.orderedStream().toList();
        this.properties = properties;
    }

    /**
     * Runs on both shutdown paths: a {@code POST /actuator/shutdown}, and SIGTERM from an
     * orchestrator or {@code docker stop}. Spring publishes {@link ContextClosedEvent} before it
     * stops any lifecycle bean or destroys anything, so the datasource, repositories and HTTP
     * server are all still usable for the duration of the drain.
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        performDrain("application context closing");
    }

    /**
     * Stops traffic, releases the streaming buffers and waits for in-flight requests to settle.
     * Repeat calls are ignored so the signal is emitted exactly once.
     */
    public void performDrain(String reason) {
        if (!drained.compareAndSet(false, true)) {
            log.debug("Drain already performed, ignoring additional trigger: {}", reason);
            return;
        }
        log.info("Graceful shutdown started ({})", reason);

        try {
            // 1. Tell everything upstream to stop routing here. Readiness drives /actuator/health/readiness
            //    to OUT_OF_SERVICE, which is what gateways and orchestrators actually watch.
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);

            // 2. Stop accepting new business requests on this instance immediately rather than
            //    waiting for the upstream to notice.
            requestTracker.beginDrain();

            // 3. Leave the discovery registry, if one is configured.
            deregister(reason);

            // 4. End the never-ending streams so they stop counting as in-flight work.
            drainSignal.signal();
            messageBuffers.complete();

            // 5. Wait for the work that is genuinely still running.
            awaitDrainedWork();
        } finally {
            completed.set(true);
        }
    }

    /**
     * @return {@code true} once a drain has been started and has not yet finished
     */
    public boolean isDraining() {
        return drained.get() && !completed.get();
    }

    /**
     * @return {@code true} once the drain sequence has run to completion
     */
    public boolean isComplete() {
        return completed.get();
    }

    private void deregister(String reason) {
        for (ServiceDeregistration deregistration : deregistrations) {
            try {
                deregistration.deregister(reason);
            } catch (RuntimeException ex) {
                // One misbehaving registry must not abort the rest of the shutdown.
                log.warn("Service deregistration failed for {}", deregistration.getClass().getName(), ex);
            }
        }
    }

    private void awaitDrainedWork() {
        Duration timeout = properties.drainTimeout();
        long startedAt = System.nanoTime();
        int outstanding = requestTracker.getInFlightCount();

        if (outstanding == 0) {
            log.info("Graceful shutdown found no in-flight requests, nothing to drain");
            return;
        }

        if (requestTracker.awaitQuiescence(timeout)) {
            log.info("Graceful shutdown drained {} in-flight request(s) in {} ms", outstanding, elapsedMillis(startedAt));
        } else {
            log.warn("Drain timeout after {} ms with {} request(s) still in flight; they may be truncated",
                    elapsedMillis(startedAt), requestTracker.getInFlightCount());
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}