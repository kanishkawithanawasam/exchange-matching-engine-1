package com.exchange.bootstrap;

import com.exchange.application.ExchangeService;
import com.exchange.application.RecoveryService;
import com.exchange.application.port.EventPublisher;
import com.exchange.application.port.CommandJournalFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Configurable construction: journal creation is supplied, never selected by this factory.
 */
public final class ExchangeFactory {
    private final RecoveryService recovery;
    private final CommandJournalFactory journals;

    public ExchangeFactory(RecoveryService recovery, CommandJournalFactory journals) {
        this.recovery = Objects.requireNonNull(recovery);
        this.journals = Objects.requireNonNull(journals);
    }

    public ExchangeService open(EventPublisher publisher) throws IOException {
        Objects.requireNonNull(publisher);
        return recovery.recover(journals.open(), publisher);
    }
}
