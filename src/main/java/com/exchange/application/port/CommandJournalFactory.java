package com.exchange.application.port;

import java.io.IOException;

/**
 * Opens a fresh journal using configuration supplied at construction.
 */
@FunctionalInterface
public interface CommandJournalFactory {
    CommandJournal open() throws IOException;
}
