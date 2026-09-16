package com.exchange.application;

import com.exchange.application.port.CommandJournal;
import com.exchange.application.port.EventPublisher;
import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.Side;
import com.exchange.testsupport.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeServiceTest {
    @Test
    void failedJournalWriteNeverReachesTheBookOrPublisher() throws Exception {
        OrderBook book = new OrderBook();
        MemoryJournal journal = new MemoryJournal();
        journal.failAppend = true;
        Events events = new Events();
        try (var service = new ExchangeService(book, journal, events)) {
            var before = book.snapshot();
            assertThrows(IOException.class, () -> service.process(Command.limit(1, Side.BUY, 100, 4)));
            assertEquals(before, book.snapshot());
            assertTrue(events.isEmpty());
            assertThrows(IllegalStateException.class, () -> service.process(Command.cancel(1)));
            assertEquals(1, journal.appendAttempts);
        }
        assertTrue(journal.closed);
    }

    @Test
    void appendCompletesBeforeAnyLiveEventAndCloseIsIdempotent() throws Exception {
        MemoryJournal journal = new MemoryJournal();
        List<Long> sequences = new ArrayList<>();
        var exchange = new RecoveryService().recover(journal, (seq, type, id, maker, price, quantity, reason) -> {
            assertEquals(seq, journal.commands.size());
            sequences.add(seq);
        });
        exchange.process(Command.limit(1, Side.SELL, 100, 4));
        exchange.process(Command.market(2, Side.BUY, 2));
        assertEquals(List.of(1L, 2L, 2L), sequences);
        assertEquals(2, exchange.snapshot().orders().get(0).remaining());
        exchange.close();
        exchange.close();
        assertEquals(1, journal.closeCalls);
        assertThrows(IllegalStateException.class, () -> exchange.process(Command.cancel(1)));
    }

    @Test
    void recoveryIsSilentAndLiveProcessingResumesAtNextSequence() throws Exception {
        MemoryJournal journal = new MemoryJournal();
        journal.commands.add(Command.limit(1, Side.SELL, 100, 4));
        journal.commands.add(Command.market(2, Side.BUY, 1));
        Events events = new Events();
        try (var exchange = new RecoveryService().recover(journal, events)) {
            assertTrue(events.isEmpty());
            assertEquals(2, exchange.snapshot().sequence());
            exchange.process(Command.market(3, Side.BUY, 3));
            assertTrue(events.stream().allMatch(event -> event.sequence() == 3));
            assertTrue(exchange.snapshot().orders().isEmpty());
        }
    }

    @Test
    void failedRecoveryClosesJournalAndPreservesOriginalFailure() {
        IOException recoveryFailure = new IOException("replay failed"), closeFailure = new IOException("close failed");
        var journal = new CommandJournal() {
            public void replay(Consumer<Command> consumer) throws IOException {
                throw recoveryFailure;
            }

            public void append(Command command) {
                fail("cannot append during failed startup");
            }

            public void close() throws IOException {
                throw closeFailure;
            }
        };
        var thrown = assertThrows(IOException.class, () -> new RecoveryService().recover(journal, EventPublisher.DISCARD));
        assertSame(recoveryFailure, thrown);
        assertArrayEquals(new Throwable[]{closeFailure}, thrown.getSuppressed());
    }

    private static final class MemoryJournal implements CommandJournal {
        private final List<Command> commands = new ArrayList<>();
        private boolean failAppend, closed;
        private int appendAttempts, closeCalls;

        public void replay(Consumer<Command> consumer) {
            commands.forEach(consumer);
        }

        public void append(Command command) throws IOException {
            appendAttempts++;
            if (failAppend) throw new IOException("injected write failure");
            commands.add(command);
        }

        public void close() {
            closed = true;
            closeCalls++;
        }
    }
}
