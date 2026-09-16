package com.exchange.infrastructure.messaging;

import com.exchange.application.port.CommandProcessor;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * Shared ownership, backpressure and completion policy; subclasses own transport mechanics.
 */
abstract class AbstractCommandProcessor implements CommandProcessor {
    private final Thread owner = Thread.currentThread();
    private final Handler handler;
    private volatile Throwable failure;
    private volatile long completed;
    private long submitted;
    private boolean closed;

    protected AbstractCommandProcessor(int capacity, Handler handler) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1)
            throw new IllegalArgumentException("capacity must be a power of two >= 2");
        this.handler = java.util.Objects.requireNonNull(handler);
    }

    protected abstract boolean tryPublish(Submission submission);

    protected abstract void stopConsumer();

    protected abstract Thread consumerThread();

    /**
     * Deadline covers waiting for capacity, not execution. False is never silently returned.
     */
    @Override
    public final void submit(Submission submission, Duration timeout) {
        checkOwner();
        checkAvailable();
        java.util.Objects.requireNonNull(submission);
        long budget = positiveNanos(timeout), start = System.nanoTime();
        while (true) {
            checkAvailable();
            boolean published = tryPublish(submission);
            if (published) {
                submitted++;
                return;
            }
            if (System.nanoTime() - start >= budget)
                throw new IllegalStateException("publication timed out; command not submitted");
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("publisher interrupted");
            LockSupport.parkNanos(1_000);
        }
    }

    @Override
    public final void awaitDrained(Duration timeout) {
        checkOwner();
        long budget = positiveNanos(timeout), start = System.nanoTime();
        while (completed != submitted) {
            checkFailure();
            if (System.nanoTime() - start >= budget) throw new IllegalStateException("drain timed out");
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("publisher interrupted");
            LockSupport.parkNanos(10_000);
        }
        checkFailure();
    }

    protected final void recordFailure(Throwable error) {
        if (failure == null) failure = java.util.Objects.requireNonNull(error);
    }

    protected final boolean hasFailed() {
        return failure != null;
    }

    protected final void handle(Submission submission) throws Exception {
        handler.accept(submission);
        completed++;
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("single producer ownership violated");
    }

    private void checkFailure() {
        if (failure != null) throw new IllegalStateException("consumer failed", failure);
    }

    private void checkAvailable() {
        if (closed) throw new IllegalStateException("pipeline closed");
        checkFailure();
    }

    private static long positiveNanos(Duration duration) {
        long n = duration.toNanos();
        if (n <= 0) throw new IllegalArgumentException("timeout must be positive");
        return n;
    }

    @Override
    public final void close() {
        checkOwner();
        if (closed) return;
        try {
            awaitDrained(Duration.ofSeconds(10));
        } finally {
            closed = true;
            stopConsumer();
            Thread consumer = consumerThread();
            try {
                consumer.join(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("shutdown interrupted", ex);
            }
            if (consumer.isAlive()) throw new IllegalStateException("consumer did not stop");
        }
    }
}
