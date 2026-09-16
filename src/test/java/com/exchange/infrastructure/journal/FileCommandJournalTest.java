package com.exchange.infrastructure.journal;

import com.exchange.application.RecoveryService;
import com.exchange.infrastructure.journal.FileJournalFactory;

import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;
import com.exchange.matching.model.RestingOrder;
import com.exchange.application.port.EventPublisher;
import com.exchange.bootstrap.ExchangeFactory;
import com.exchange.infrastructure.journal.FileCommandJournal;
import com.exchange.testsupport.TestWorkloads;
import com.exchange.testsupport.Events;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import static com.exchange.matching.model.Side.*;
import static java.nio.file.StandardOpenOption.*;
import static org.junit.jupiter.api.Assertions.*;

class FileCommandJournalTest {
    @TempDir
    Path dir;

    @Test
    void recoversOriginalWriterFixtureAndContinues() throws Exception {
        Path path = dir.resolve("original-v1");
        try (var source = getClass().getResourceAsStream("/journal/v1-compatibility.bin")) {
            assertNotNull(source);
            Files.copy(source, path);
        }
        try (var exchange = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            assertEquals(new BookSnapshot(4, 3, 2, List.of(new RestingOrder(2, SELL, 100, 6))), exchange.snapshot());
            exchange.process(Command.market(4, BUY, 6));
        }
        try (var exchange = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            assertEquals(new BookSnapshot(5, 4, 3, List.of()), exchange.snapshot());
        }
    }

    @Test
    void newWriterPreservesOriginalJournalBytes() throws Exception {
        Path path = dir.resolve("new-v1");
        try (var exchange = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            exchange.process(Command.limit(1, SELL, 100, 5));
            exchange.process(Command.limit(2, SELL, 100, 7));
            exchange.process(Command.market(3, BUY, 6));
            exchange.process(Command.limit(2, BUY, 90, 1));
        }
        try (var source = getClass().getResourceAsStream("/journal/v1-compatibility.bin")) {
            assertNotNull(source);
            assertArrayEquals(source.readAllBytes(), Files.readAllBytes(path));
        }
    }

    @Test
    void journalRequiresOneSuccessfulReplayBeforeAppend() throws Exception {
        try (var journal = new FileCommandJournal(dir.resolve("lifecycle"), FileCommandJournal.Durability.SYNC)) {
            assertThrows(IOException.class, () -> journal.append(Command.cancel(1)));
            journal.replay(command -> fail("new file must be empty"));
            journal.append(Command.cancel(1));
            assertThrows(IOException.class, () -> journal.replay(command -> {
            }));
        }
    }

    @Test
    void replayCallbackFailurePreventsSubsequentAppend() throws Exception {
        Path path = dir.resolve("replay-callback");
        try (var exchange = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            exchange.process(Command.market(1, BUY, 1));
        }
        try (var journal = new FileCommandJournal(path, FileCommandJournal.Durability.SYNC)) {
            assertThrows(IllegalStateException.class, () -> journal.replay(command -> {
                throw new IllegalStateException("injected");
            }));
            assertThrows(IOException.class, () -> journal.append(Command.cancel(1)));
        }
    }

    @Test
    void replayMatchesStateAndFullEventStreamIncludingRejectsAndContinues() throws Exception {
        Path path = dir.resolve("orders");
        var liveEvents = new Events();
        var replayEvents = new Events();
        List<Command> commands = new ArrayList<>(Arrays.asList(TestWorkloads.cycle(8000)));
        commands.add(Command.limit(8001, BUY, 95, 12));
        commands.add(Command.limit(8002, BUY, 95, 4));
        commands.add(Command.limit(8001, BUY, 99, 1));
        commands.add(Command.cancel(999999));
        commands.add(null);
        BookSnapshot expected;
        try (var live = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.OS_BUFFERED)).open(liveEvents)) {
            for (var c : commands) live.process(c);
            expected = live.snapshot();
        }
        var replay = new OrderBook();
        try (var ignored = new FileCommandJournal(path, FileCommandJournal.Durability.SYNC)) {
            ignored.replay(command -> replay.process(command, replayEvents));
            assertEquals(expected, replay.snapshot());
            assertEquals(liveEvents, replayEvents);
        }
        var silentEvents = new Events();
        try (var recovered = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(silentEvents)) {
            assertTrue(silentEvents.isEmpty());
            recovered.process(Command.market(8003, SELL, 13));
            assertEquals(expected.sequence() + 1, recovered.snapshot().sequence());
            assertEquals(List.of(new RestingOrder(8002, BUY, 95, 3)), recovered.snapshot().orders());
        }
        try (var recoveredAgain = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            assertEquals(expected.sequence() + 1, recoveredAgain.snapshot().sequence());
        }
    }

    @Test
    void everyIncompleteTailIsTruncatedBeforeNextAppend() throws Exception {
        Path complete = dir.resolve("complete");
        try (var live = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(complete, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            live.process(Command.limit(1, SELL, 100, 3));
            live.process(Command.market(2, BUY, 1));
        }
        byte[] bytes = Files.readAllBytes(complete);
        for (int tail = 1; tail < FileCommandJournal.RECORD_BYTES; tail++) {
            Path path = dir.resolve("tail-" + tail);
            Files.write(path, Arrays.copyOf(bytes, FileCommandJournal.HEADER_BYTES + FileCommandJournal.RECORD_BYTES + tail));
            try (var recovered = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
                assertEquals(1, recovered.snapshot().sequence());
                assertEquals(FileCommandJournal.HEADER_BYTES + FileCommandJournal.RECORD_BYTES, Files.size(path));
                recovered.process(Command.market(2, BUY, 2));
            }
            try (var again = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
                assertEquals(1, again.snapshot().orders().get(0).remaining());
            }
        }
    }

    @Test
    void fullCorruptRecordOrUnknownVersionFailsWithoutTruncating() throws Exception {
        Path path = dir.resolve("corrupt");
        try (var live = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            live.process(Command.limit(1, SELL, 100, 3));
        }
        byte[] valid = Files.readAllBytes(path);
        for (int offset : new int[]{0, 7, 8, valid.length - 1}) {
            byte[] bad = valid.clone();
            bad[offset] ^= 0x7f;
            Files.write(path, bad);
            assertThrows(IOException.class, () -> new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD));
            assertArrayEquals(bad, Files.readAllBytes(path));
        }
        Files.write(path, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD));
    }

    @Test
    void validChecksumCannotHideSequenceGap() throws Exception {
        Path path = dir.resolve("sequence");
        try (var live = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            live.process(Command.market(1, BUY, 1));
        }
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).putLong(FileCommandJournal.HEADER_BYTES, 2);
        var crc = new java.util.zip.CRC32C();
        crc.update(bytes, FileCommandJournal.HEADER_BYTES, FileCommandJournal.RECORD_BYTES - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
        Files.write(path, bytes);
        assertThrows(IOException.class, () -> new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD));
    }

    @Test
    void fileLockPreventsTwoWritersAndCloseDisallowsFurtherCommands() throws Exception {
        Path path = dir.resolve("locked");
        var first = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD);
        try (first) {
            assertThrows(java.nio.channels.OverlappingFileLockException.class,
                    () -> new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD));
        }
        assertThrows(IllegalStateException.class, () -> first.process(Command.cancel(1)));
        try (var ignored = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) { /* lock released */ }
    }

    @Test
    void callbackFailurePoisonsInstanceAndRecoveryAppliesDurableCommandExactlyOnce() throws Exception {
        Path path = dir.resolve("callback-failure");
        try (var live = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open((s, t, i, m, p, q, r) -> {
            throw new IllegalStateException("broken sink");
        })) {
            assertThrows(IllegalStateException.class, () -> live.process(Command.limit(1, SELL, 100, 5)));
            assertThrows(IllegalStateException.class, live::snapshot);
            assertThrows(IllegalStateException.class, () -> live.process(Command.cancel(1)));
        }
        try (var recovered = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            assertEquals(1, recovered.snapshot().sequence());
            assertEquals(5, recovered.snapshot().orders().get(0).remaining());
        }
    }

    @Test
    void abruptProcessExitRecoversForcedCommandsWithoutClose() throws Exception {
        Path path = dir.resolve("crash");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), CrashWriter.class.getName(), path.toString())
                .redirectErrorStream(true).redirectOutput(dir.resolve("child.log").toFile()).start();
        try {
            assertTrue(child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(23, child.exitValue());
        } finally {
            child.destroyForcibly();
        }
        try (var recovered = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(path, FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD)) {
            assertEquals(2, recovered.snapshot().sequence());
            assertEquals(List.of(new RestingOrder(1, BUY, 100, 4)), recovered.snapshot().orders());
        }
    }

    public static class CrashWriter {
        public static void main(String[] args) throws Exception {
            var exchange = new ExchangeFactory(new RecoveryService(), new FileJournalFactory(Path.of(args[0]), FileCommandJournal.Durability.SYNC)).open(EventPublisher.DISCARD);
            exchange.process(Command.limit(1, BUY, 100, 7));
            exchange.process(Command.market(2, SELL, 3));
            Runtime.getRuntime().halt(23);
        }
    }
}
