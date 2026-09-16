package com.exchange.infrastructure.journal;

import com.exchange.application.port.CommandJournal;
import com.exchange.application.port.CommandJournalFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable filesystem configuration; each open owns a new journal lifecycle.
 */
public final class FileJournalFactory implements CommandJournalFactory {
    private final Path path;
    private final FileCommandJournal.Durability durability;

    public FileJournalFactory(Path path, FileCommandJournal.Durability durability) {
        this.path = Objects.requireNonNull(path);
        this.durability = Objects.requireNonNull(durability);
    }

    @Override
    public CommandJournal open() throws IOException {
        return new FileCommandJournal(path, durability);
    }
}
