package com.exchange.bootstrap;

import com.exchange.application.port.CommandProcessor;
import com.exchange.application.port.CommandProcessorFactory;
import com.exchange.infrastructure.messaging.BlockingQueueProcessor;
import com.exchange.infrastructure.messaging.DisruptorProcessor;

/**
 * Transport selection belongs at the composition root, not in either implementation.
 */
public final class ProcessorFactory implements CommandProcessorFactory {
    public enum Kind {QUEUE, DISRUPTOR}

    private final Kind kind;
    private final DisruptorProcessor.WaitMode waitMode;

    public ProcessorFactory(Kind kind, boolean yielding) {
        this.kind = java.util.Objects.requireNonNull(kind);
        if (kind == Kind.QUEUE && yielding) throw new IllegalArgumentException("queue does not support yielding wait");
        waitMode = yielding ? DisruptorProcessor.WaitMode.YIELDING : DisruptorProcessor.WaitMode.BLOCKING;
    }

    @Override
    public CommandProcessor create(int capacity, CommandProcessor.Handler handler) {
        return switch (kind) {
            case QUEUE -> new BlockingQueueProcessor(capacity, handler);
            case DISRUPTOR -> new DisruptorProcessor(capacity, handler, waitMode);
        };
    }
}
