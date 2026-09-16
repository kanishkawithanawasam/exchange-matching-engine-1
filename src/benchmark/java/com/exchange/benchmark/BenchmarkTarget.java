package com.exchange.benchmark;

import com.exchange.application.ExchangeService;
import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Select persistence outside the measured command handler. Only benchmark code has this bypass.
 */
interface BenchmarkTarget extends AutoCloseable {
    void process(Command command) throws IOException;

    BookSnapshot snapshot();

    @Override
    void close() throws IOException;

    final class InMemory implements BenchmarkTarget {
        private final OrderBook book = new OrderBook();
        private final EventDigest events;

        InMemory(EventDigest events) {
            this.events = events;
        }

        public void process(Command command) {
            book.process(command, events);
        }

        public BookSnapshot snapshot() {
            return book.snapshot();
        }

        public void close() {
        }
    }

    record Durable(Path path, ExchangeService exchange) implements BenchmarkTarget {
        public void process(Command command) throws IOException {
            exchange.process(command);
        }

        public BookSnapshot snapshot() {
            return exchange.snapshot();
        }

        public void close() throws IOException {
            try {
                exchange.close();
            } catch (IOException | RuntimeException | Error error) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupFailure) {
                    error.addSuppressed(cleanupFailure);
                }
                throw error;
            }
            Files.deleteIfExists(path);
        }
    }
}
