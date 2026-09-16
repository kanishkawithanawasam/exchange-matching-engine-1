package com.exchange.infrastructure.messaging;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bounded ArrayBlockingQueue adapter, with exactly one consumer.
 */
public final class BlockingQueueProcessor extends AbstractCommandProcessor {
    private final ArrayBlockingQueue<Submission> queue;
    private final Thread consumer;
    private volatile boolean stopping;

    public BlockingQueueProcessor(int capacity, Handler handler) {
        super(capacity, handler);
        queue = new ArrayBlockingQueue<>(capacity);
        consumer = new Thread(this::consume, "matching-queue");
        consumer.setDaemon(true);
        consumer.start();
    }

    @Override
    protected boolean tryPublish(Submission submission) {
        return queue.offer(submission);
    }

    @Override
    protected Thread consumerThread() {
        return consumer;
    }

    @Override
    protected void stopConsumer() {
        stopping = true;
        consumer.interrupt();
    }

    private void consume() {
        try {
            while (!stopping) {
                Submission submission = queue.poll(10, TimeUnit.MILLISECONDS);
                if (submission != null) handle(submission);
            }
        } catch (InterruptedException error) {
            if (!stopping) recordFailure(error);
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            recordFailure(error);
        }
    }
}
