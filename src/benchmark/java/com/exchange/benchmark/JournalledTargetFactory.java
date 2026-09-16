package com.exchange.benchmark;

import com.exchange.bootstrap.ExchangeFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/**
 * Owns temporary benchmark files; the caller supplies exchange construction and its durability policy.
 */
final class JournalledTargetFactory implements BenchmarkTargetFactory {
    private final Function<Path, ExchangeFactory> exchanges;

    JournalledTargetFactory(Function<Path, ExchangeFactory> exchanges) {
        this.exchanges = Objects.requireNonNull(exchanges);
    }

    @Override
    public BenchmarkTarget open(EventDigest events) throws IOException {
        Path path = Files.createTempFile("exchange-benchmark-", ".journal");
        try {
            return new BenchmarkTarget.Durable(path, exchanges.apply(path).open(events));
        } catch (IOException | RuntimeException | Error error) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupFailure) {
                error.addSuppressed(cleanupFailure);
            }
            throw error;
        }
    }
}
