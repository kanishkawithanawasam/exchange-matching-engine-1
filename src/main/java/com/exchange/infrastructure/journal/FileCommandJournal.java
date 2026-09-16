package com.exchange.infrastructure.journal;

import com.exchange.application.port.CommandJournal;
import com.exchange.matching.command.Command;

import java.util.function.Consumer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

/**
 * Versioned, fixed-width, big-endian command log. Owns an exclusive process lock.
 */
public final class FileCommandJournal implements CommandJournal {
    public enum Durability {SYNC, OS_BUFFERED}

    private static final int MAGIC = 0x4d415443;
    static final int HEADER_BYTES = 8;
    static final int RECORD_BYTES = JournalRecordCodec.RECORD_BYTES;
    private final FileChannel channel;
    private final FileLock lock;
    private final Durability durability;
    private final ByteBuffer buffer = ByteBuffer.allocate(RECORD_BYTES);
    private final JournalRecordCodec codec = new JournalRecordCodec();
    private long sequence;
    private boolean failed;
    private boolean recovered;
    private boolean closed;

    /**
     * Opens and locks the file. Call replay once before appending; close if recovery fails.
     */
    public FileCommandJournal(Path path, Durability durability) throws IOException {
        this.durability = java.util.Objects.requireNonNull(durability);
        channel = FileChannel.open(path, CREATE, READ, WRITE);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("journal already locked");
            lock = acquired;
        } catch (Throwable error) {
            if (acquired != null) acquired.release();
            channel.close();
            throw error;
        }
    }

    @Override
    public void replay(Consumer<Command> target) throws IOException {
        java.util.Objects.requireNonNull(target);
        if (recovered || failed || closed) throw new IOException("journal cannot be replayed in its current state");
        try {
            recover(target);
            recovered = true;
        } catch (IOException | RuntimeException | Error error) {
            failed = true;
            throw error;
        }
    }

    private void recover(Consumer<Command> target) throws IOException {
        if (channel.size() == 0) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).putInt(MAGIC).putInt(1);
            header.flip();
            writeFully(header);
            channel.force(true);
        }
        channel.position(0);
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        readFully(header);
        header.flip();
        if (header.getInt() != MAGIC || header.getInt() != 1) throw new IOException("unsupported journal header");
        long completeEnd = HEADER_BYTES + ((channel.size() - HEADER_BYTES) / RECORD_BYTES) * RECORD_BYTES;
        while (channel.position() < completeEnd) {
            buffer.clear();
            readFully(buffer);
            buffer.flip();
            target.accept(codec.decode(buffer, Math.incrementExact(sequence)));
            sequence++;
        }
        if (channel.size() != completeEnd) {
            channel.truncate(completeEnd);
            channel.force(true);
        }
        channel.position(completeEnd);
    }

    public void append(Command command) throws IOException {
        if (failed || closed || !recovered) throw new IOException("journal unavailable; replay once before append");
        try {
            codec.encode(command, Math.incrementExact(sequence), buffer);
            writeFully(buffer);
            if (durability == Durability.SYNC) channel.force(true);
            sequence++;
        } catch (IOException | RuntimeException | Error error) {
            failed = true;
            throw error;
        }
    }

    private void readFully(ByteBuffer target) throws IOException {
        while (target.hasRemaining())
            if (channel.read(target) < 0) throw new IOException("truncated journal header/record");
    }

    private void writeFully(ByteBuffer source) throws IOException {
        while (source.hasRemaining()) channel.write(source);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            channel.force(true);
        } finally {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }
}
