package com.spring.api.hub.lifecycle;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageBufferRegistryTest {

    @Test
    void completesRegisteredBuffersSoSubscribersFinish() {
        MessageBufferRegistry registry = new MessageBufferRegistry();
        Sinks.Many<String> buffer = Sinks.many().multicast().onBackpressureBuffer();
        registry.register(buffer);

        registry.complete();

        StepVerifier.create(buffer.asFlux()).verifyComplete();
    }

    @Test
    void reportsBuffersThatWereAlreadyFinished() {
        MessageBufferRegistry registry = new MessageBufferRegistry();
        Sinks.Many<String> finished = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.Many<String> open = Sinks.many().multicast().onBackpressureBuffer();
        registry.register(finished);
        registry.register(open);
        finished.tryEmitComplete();

        assertEquals(1, registry.complete(), "only the buffer that was already finished counts");
        assertEquals(2, registry.complete(), "once finished, buffers stay finished");
    }

    @Test
    void ignoresNullRegistrations() {
        MessageBufferRegistry registry = new MessageBufferRegistry();

        registry.register(null);

        assertEquals(0, registry.size());
        assertEquals(0, registry.complete());
    }

    @Test
    void toleratesAnEmptyRegistry() {
        MessageBufferRegistry registry = new MessageBufferRegistry();

        assertEquals(0, registry.complete());
    }
}