package com.exchange.application;

import com.exchange.application.port.CommandJournal;
import com.exchange.application.port.EventPublisher;
import com.exchange.matching.OrderBook;
import com.exchange.matching.event.EventSink;

import java.io.IOException;
import java.util.Objects;

/**
 * Rebuilds a fresh aggregate without publishing historical events. Takes ownership of the journal.
 */
public final class RecoveryService {
    public ExchangeService recover(CommandJournal journal, EventPublisher publisher) throws IOException {
        Objects.requireNonNull(journal);
        try {
            Objects.requireNonNull(publisher);
            OrderBook book = new OrderBook();
            journal.replay(command -> book.process(command, EventSink.DISCARD));
            return new ExchangeService(book, journal, publisher);
        } catch (IOException | RuntimeException | Error error) {
            try {
                journal.close();
            } catch (Throwable closeFailure) {
                error.addSuppressed(closeFailure);
            }
            throw error;
        }
    }
}
