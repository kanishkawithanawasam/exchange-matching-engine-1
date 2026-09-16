package com.exchange.infrastructure.messaging;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.ThreadFactory;

/**
 * Single-producer Disruptor adapter. Slots are released only after processing.
 */
public final class DisruptorProcessor extends AbstractCommandProcessor {
    public enum WaitMode {BLOCKING, YIELDING}

    private static final class Slot {
        private Submission value;
    }

    private final Disruptor<Slot> disruptor;
    private final RingBuffer<Slot> ring;
    private final WorkerThreads threads = new WorkerThreads();

    public DisruptorProcessor(int capacity, Handler handler, WaitMode waitMode) {
        super(capacity, handler);
        java.util.Objects.requireNonNull(waitMode);
        disruptor = new Disruptor<>(Slot::new, capacity, threads, ProducerType.SINGLE,
                waitMode == WaitMode.YIELDING ? new YieldingWaitStrategy() : new BlockingWaitStrategy());
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<>() {
            public void handleEventException(Throwable error, long sequence, Slot event) {
                recordFailure(error);
            }

            public void handleOnStartException(Throwable error) {
                recordFailure(error);
            }

            public void handleOnShutdownException(Throwable error) {
                recordFailure(error);
            }
        });
        disruptor.handleEventsWith((slot, sequence, endOfBatch) -> {
            try {
                if (!hasFailed()) handle(slot.value);
            } finally {
                slot.value = null;
            }
        });
        ring = disruptor.start();
    }

    @Override
    protected boolean tryPublish(Submission submission) {
        return ring.tryPublishEvent((slot, sequence, value) -> slot.value = value, submission);
    }

    @Override
    protected Thread consumerThread() {
        return threads.worker;
    }

    @Override
    protected void stopConsumer() {
        disruptor.halt();
    }

    private static final class WorkerThreads implements ThreadFactory {
        private Thread worker;

        public Thread newThread(Runnable runnable) {
            worker = new Thread(runnable, "matching-disruptor");
            worker.setDaemon(true);
            return worker;
        }
    }
}
