package com.spring.api.hub.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownCoordinatorTest {

    private AnnotationConfigApplicationContext context;
    private GracefulShutdownCoordinator coordinator;
    private InFlightRequestTracker tracker;
    private DrainSignal drainSignal;
    private MessageBufferRegistry messageBuffers;
    private final List<ReadinessState> readinessStates = new CopyOnWriteArrayList<>();
    private final List<String> deregistrationReasons = new CopyOnWriteArrayList<>();

    /**
     * Uses AnnotationConfigApplicationContext rather than GenericApplicationContext because
     * only the former processes {@code @EventListener}, and the ContextClosedEvent wiring is
     * precisely what these tests need to exercise.
     */
    private void startContext(Duration drainTimeout, ServiceDeregistration... deregistrations) {
        context = new AnnotationConfigApplicationContext();
        readinessStates.clear();
        deregistrationReasons.clear();
        context.addApplicationListener((ApplicationListener<AvailabilityChangeEvent<ReadinessState>>) event -> readinessStates
                .add(event.getState()));

        context.registerBean(InFlightRequestTracker.class);
        context.registerBean(DrainSignal.class);
        context.registerBean(MessageBufferRegistry.class);
        context.registerBean(ShutdownProperties.class, () -> new ShutdownProperties(drainTimeout, "token"));
        for (int i = 0; i < deregistrations.length; i++) {
            ServiceDeregistration deregistration = deregistrations[i];
            context.registerBean("deregistration" + i, ServiceDeregistration.class, () -> deregistration);
        }
        context.register(GracefulShutdownCoordinator.class);
        context.refresh();

        tracker = context.getBean(InFlightRequestTracker.class);
        drainSignal = context.getBean(DrainSignal.class);
        messageBuffers = context.getBean(MessageBufferRegistry.class);
        coordinator = context.getBean(GracefulShutdownCoordinator.class);
    }

    @BeforeEach
    void setUp() {
        startContext(Duration.ofSeconds(2), reason -> deregistrationReasons.add(reason));
    }

    @Test
    void stopsAcceptingTrafficAndLeavesRotationOnDrain() {
        coordinator.performDrain("test");

        assertTrue(tracker.isDraining());
        assertTrue(drainSignal.isDraining());
        assertEquals(List.of(ReadinessState.REFUSING_TRAFFIC), readinessStates,
                "readiness must flip so upstream stops routing here");
        assertEquals(List.of("test"), deregistrationReasons);
    }

    @Test
    void completesMessageBuffersOnDrain() {
        Sinks.Many<String> buffer = Sinks.many().multicast().onBackpressureBuffer();
        messageBuffers.register(buffer);

        coordinator.performDrain("test");

        StepVerifier.create(buffer.asFlux()).verifyComplete();
    }

    @Test
    void reportsReadinessBeforeDeregisteringSoTrafficHasStoppedFirst() {
        startContext(Duration.ofSeconds(2), reason -> {
            // The whole point of deregistering after the readiness flip is that a caller which
            // only watches probes has already stopped sending traffic by this point.
            assertEquals(List.of(ReadinessState.REFUSING_TRAFFIC), readinessStates);
            assertTrue(tracker.isDraining(), "local requests must already be refused too");
        });

        coordinator.performDrain("ordering");
    }

    @Test
    void waitsForInFlightRequestsBeforeFinishing() throws Exception {
        tracker.beginRequest();
        Thread releaser = new Thread(() -> {
            sleepQuietly(100);
            tracker.endRequest();
        });
        releaser.start();

        coordinator.performDrain("test");

        releaser.join();
        assertEquals(0, tracker.getInFlightCount());
    }

    @Test
    void stillCompletesTheDrainWhenARequestOverrunsTheTimeout() {
        startContext(Duration.ofMillis(150));
        tracker.beginRequest();

        coordinator.performDrain("test");

        assertTrue(tracker.isDraining(), "the drain must still finish rather than hang on a stuck request");
        assertEquals(1, tracker.getInFlightCount());
    }

    @Test
    void drainsOnlyOnce() {
        coordinator.performDrain("first");
        readinessStates.clear();
        deregistrationReasons.clear();

        coordinator.performDrain("second");

        assertTrue(readinessStates.isEmpty(), "readiness must not be republished");
        assertTrue(deregistrationReasons.isEmpty());
    }

    @Test
    void survivesADeregistrationThatThrows() {
        startContext(Duration.ofMillis(100), reason -> {
            throw new IllegalStateException("registry unreachable");
        }, reason -> deregistrationReasons.add(reason));

        coordinator.performDrain("test");

        assertTrue(tracker.isDraining());
        assertTrue(drainSignal.isDraining());
        assertEquals(List.of("test"), deregistrationReasons, "a later registry must still run");
    }

    @Test
    void drainsWhenTheApplicationContextCloses() {
        tracker.beginRequest();
        tracker.endRequest();

        context.publishEvent(new ContextClosedEvent(context));

        assertTrue(tracker.isDraining());
        assertEquals(List.of(ReadinessState.REFUSING_TRAFFIC), readinessStates);
    }

    @Test
    void worksWithoutAnyDeregistrationBean() {
        startContext(Duration.ofSeconds(1));
        coordinator.performDrain("test");

        assertTrue(tracker.isDraining());
    }

    @Test
    void publishesNoReadinessChangeBeforeShutdown() {
        assertEquals(new ArrayList<ReadinessState>(), readinessStates);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}