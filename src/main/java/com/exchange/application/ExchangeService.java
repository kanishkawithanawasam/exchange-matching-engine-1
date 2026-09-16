package com.exchange.application;

import com.exchange.application.port.CommandJournal;
import com.exchange.application.port.EventPublisher;
import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;

import java.io.IOException;

/**
 * Owns the journal-before-match use case and fail-stop lifecycle. No transport or file dependencies.
 */
public final class ExchangeService implements AutoCloseable {
    private final OrderBook book;
    private final CommandJournal journal;
    private final EventPublisher publisher;
    private boolean failed;
    private boolean closed;

    ExchangeService(OrderBook book, CommandJournal journal, EventPublisher publisher) {
        this.book = java.util.Objects.requireNonNull(book);
        this.journal = java.util.Objects.requireNonNull(journal);
        this.publisher = java.util.Objects.requireNonNull(publisher);
    }

    public void process(Command command) throws IOException {
        if (failed || closed) throw new IllegalStateException("exchange unavailable; close and recover");
        try {
            journal.append(command);
            book.process(command, publisher);
        } catch (IOException | RuntimeException | Error error) {
            failed = true;
            throw error;
        }
    }

    public BookSnapshot snapshot() {
        if (failed) throw new IllegalStateException("state may be incomplete; recover first");
        return book.snapshot();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            journal.close();
        }
    }
}
