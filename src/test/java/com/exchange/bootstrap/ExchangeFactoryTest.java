package com.exchange.bootstrap;

import com.exchange.application.RecoveryService;
import com.exchange.application.port.CommandJournal;
import com.exchange.application.port.EventPublisher;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.Side;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeFactoryTest {
    @Test
    void injectedJournalFactoryCreatesIndependentExchangeLifecycles() throws Exception {
        AtomicInteger opened = new AtomicInteger(), closed = new AtomicInteger();
        ExchangeFactory factory = new ExchangeFactory(new RecoveryService(), () -> {
            opened.incrementAndGet();
            return new CommandJournal() {
                public void replay(Consumer<Command> consumer) {
                }

                public void append(Command command) {
                }

                public void close() {
                    closed.incrementAndGet();
                }
            };
        });
        try (var first = factory.open(EventPublisher.DISCARD); var second = factory.open(EventPublisher.DISCARD)) {
            first.process(Command.limit(1, Side.BUY, 100, 5));
            assertEquals(1, first.snapshot().sequence());
            assertEquals(0, second.snapshot().sequence());
            assertTrue(second.snapshot().orders().isEmpty());
        }
        assertEquals(2, opened.get());
        assertEquals(2, closed.get());
    }

    @Test
    void resourceCreationFailureIsPropagatedAndNullPublisherOpensNothing() {
        IOException failure = new IOException("injected storage outage");
        AtomicInteger opened = new AtomicInteger();
        var factory = new ExchangeFactory(new RecoveryService(), () -> {
            opened.incrementAndGet();
            throw failure;
        });
        assertThrows(NullPointerException.class, () -> factory.open(null));
        assertEquals(0, opened.get());
        assertSame(failure, assertThrows(IOException.class, () -> factory.open(EventPublisher.DISCARD)));
        assertEquals(1, opened.get());
    }
}
