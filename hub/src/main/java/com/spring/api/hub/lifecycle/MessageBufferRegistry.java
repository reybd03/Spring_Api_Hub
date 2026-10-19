package com.spring.api.hub.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Sinks;

/**
 * Holds the in-process message buffers so shutdown terminates them instead of dropping
 * whatever they still hold on the floor when the context closes.
 */
@Component
public class MessageBufferRegistry {

    private static final Logger log = LoggerFactory.getLogger(MessageBufferRegistry.class);

    private final List<Sinks.Many<?>> buffers = new CopyOnWriteArrayList<>();

    public void register(Sinks.Many<?> buffer) {
        if (buffer != null) {
            buffers.add(buffer);
        }
    }

    /**
     * Completes every registered buffer, which lets downstream subscribers finish reading and
     * stops the buffers from holding the instance open.
     *
     * @return how many buffers were already finished before this call
     */
    public int complete() {
        int alreadyFinished = 0;
        for (Sinks.Many<?> buffer : buffers) {
            try {
                if (buffer.tryEmitComplete() == Sinks.EmitResult.FAIL_TERMINATED) {
                    alreadyFinished++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not complete message buffer during drain", ex);
            }
        }
        if (!buffers.isEmpty()) {
            log.info("Completed {} message buffer(s), {} already finished", buffers.size() - alreadyFinished,
                    alreadyFinished);
        }
        return alreadyFinished;
    }

    public int size() {
        return buffers.size();
    }
}