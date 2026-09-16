package com.exchange.application.port;

import com.exchange.matching.command.Command;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Single-owner write-ahead log. Replay once before append; implementations preserve command order.
 */
public interface CommandJournal extends AutoCloseable {
    void replay(Consumer<Command> consumer) throws IOException;

    /**
     * Return only after the configured persistence boundary has been reached.
     */
    void append(Command command) throws IOException;

    @Override
    void close() throws IOException;
}
