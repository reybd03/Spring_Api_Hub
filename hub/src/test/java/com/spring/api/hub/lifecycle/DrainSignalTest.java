package com.spring.api.hub.lifecycle;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrainSignalTest {

    @Test
    void staysOpenWhileTheServiceIsHealthy() {
        DrainSignal signal = new DrainSignal();

        assertFalse(signal.isDraining());
        // Never completes, which is exactly what keeps the SSE streams alive.
        StepVerifier.create(signal.drain())
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(200))
                .thenCancel()
                .verify();
    }

    @Test
    void completesSubscribersOnSignal() {
        DrainSignal signal = new DrainSignal();

        signal.signal();

        assertTrue(signal.isDraining());
        StepVerifier.create(signal.drain()).verifyComplete();
    }

    @Test
    void signalsOnlyOnceSoLateSubscribersStillObserveTheDrain() {
        DrainSignal signal = new DrainSignal();

        signal.signal();
        signal.signal();

        assertTrue(signal.isDraining());
        StepVerifier.create(signal.drain()).verifyComplete();
    }
}