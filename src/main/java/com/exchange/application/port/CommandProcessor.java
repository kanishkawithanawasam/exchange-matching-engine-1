package com.exchange.application.port;

import com.exchange.matching.command.Command;

import java.time.Duration;

/**
 * Single producer, single consumer ingress. Successful submission means enqueued, not durable.
 */
public interface CommandProcessor extends AutoCloseable {
    /**
     * Caller-owned correlation and timing metadata; the matching domain never sees these fields.
     */
    record Submission(Command command, int index, long startedNanos) {
    }

    @FunctionalInterface
    interface Handler {
        void accept(Submission submission) throws Exception;
    }

    void submit(Submission submission, Duration timeout);

    void awaitDrained(Duration timeout);

    @Override
    void close();
}
