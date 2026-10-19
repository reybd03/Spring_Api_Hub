package com.spring.api.hub.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Broadcasts "this instance is draining" so long-lived reactive streams can end on their own.
 *
 * <p>The SSE feeds in this application poll forever by design. Without a drain signal they
 * would keep the instance pinned as in-flight work until the shutdown timeout expired, turning
 * every restart into a fixed multi-second stall. Consumers compose it with
 * {@code takeUntilOther(drainSignal.drain())} to terminate cleanly instead.
 */
@Component
public class DrainSignal {

    private final Sinks.Empty<Void> sink = Sinks.empty();
    private final AtomicBoolean draining = new AtomicBoolean();

    /**
     * Completes as soon as a drain begins and never completes while the service is healthy.
     */
    public Mono<Void> drain() {
        return sink.asMono();
    }

    public boolean isDraining() {
        return draining.get();
    }

    /**
     * Emits the drain signal. Repeat calls are ignored so subscribers are completed once.
     */
    public void signal() {
        if (draining.compareAndSet(false, true)) {
            sink.tryEmitEmpty();
        }
    }
}