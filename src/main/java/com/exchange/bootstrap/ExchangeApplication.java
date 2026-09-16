package com.exchange.bootstrap;

import com.exchange.application.RecoveryService;
import com.exchange.infrastructure.journal.FileJournalFactory;

import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;
import com.exchange.application.port.EventPublisher;
import com.exchange.infrastructure.journal.FileCommandJournal;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.exchange.matching.model.Side.*;

public final class ExchangeApplication {
    private final java.io.PrintStream output;
    private final java.util.function.Function<Path, ExchangeFactory> exchanges;

    public ExchangeApplication(java.io.PrintStream output, java.util.function.Function<Path, ExchangeFactory> exchanges) {
        this.output = java.util.Objects.requireNonNull(output);
        this.exchanges = java.util.Objects.requireNonNull(exchanges);
    }

    public static void main(String[] args) throws Exception {
        RecoveryService recovery = new RecoveryService();
        new ExchangeApplication(System.out, path -> new ExchangeFactory(recovery,
                new FileJournalFactory(path, FileCommandJournal.Durability.SYNC))).run(args);
    }

    public void run(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("recover")) {
            try (var exchange = exchanges.apply(Path.of(args[1])).open(EventPublisher.DISCARD)) {
                output.println(exchange.snapshot());
            }
            return;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equals("demo")))
            throw new IllegalArgumentException("Usage: java -jar target/exchange.jar [demo | recover PATH]");
        Path journal = Files.createTempFile("exchange-demo-", ".journal");
        EventPublisher print = (sequence, type, id, maker, price, qty, reason) ->
                output.printf("seq=%d %-9s order=%d maker=%d price=%d qty=%d %s%n", sequence, type, id, maker, price, qty, reason);
        ExchangeFactory factory = exchanges.apply(journal);
        BookSnapshot before;
        try (var exchange = factory.open(print)) {
            exchange.process(Command.limit(1, SELL, 101, 7));
            exchange.process(Command.limit(2, SELL, 101, 5));
            exchange.process(Command.limit(3, BUY, 101, 9));
            exchange.process(Command.cancel(2));
            exchange.process(Command.market(4, BUY, 3));
            exchange.process(Command.limit(5, BUY, 99, 10));
            before = exchange.snapshot();
        }
        try (var recovered = factory.open(print)) {
            if (!before.equals(recovered.snapshot())) throw new IllegalStateException("replay mismatch");
            output.println("Recovery verified: " + recovered.snapshot());
            output.println("Journal retained at " + journal);
        }
    }
}
