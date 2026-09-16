package com.exchange.benchmark;

import com.exchange.bootstrap.ProcessorFactory;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkComponentsTest {
    private final BenchmarkOptions options = new BenchmarkOptions(ProcessorFactory.Kind.QUEUE, 8, 0, 16, "blocking", "none", 1);

    @Test
    void measurementsOwnTheirSamplesAndUseNearestRank() {
        long[] samples = {40, 10, 30, 20};
        BenchmarkResult result = new BenchmarkResult(samples, 100, 0, 0);
        samples[0] = 999;
        assertEquals(20, result.percentile(.5));
        assertEquals(40, result.maximum());
        assertThrows(IllegalArgumentException.class, () -> result.percentile(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> result.percentile(0));
    }

    @Test
    void reporterWritesToSuppliedDestinationAndReportsOutputFailure() {
        var report = new BenchmarkReport(options, new BenchmarkResult(new long[]{10}, 100, 0, 0),
                new EventDigest().snapshot(), "custom");
        StringWriter destination = new StringWriter();
        new CsvReporter(new PrintWriter(destination), List.of("environment=test")).write(report);
        assertTrue(destination.toString().contains("# workload=custom"));
        assertTrue(destination.toString().contains("# environment=test"));
        Writer broken = new Writer() {
            public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("output failed");
            }

            public void flush() {
            }

            public void close() {
            }
        };
        assertThrows(IllegalStateException.class, () -> new CsvReporter(new PrintWriter(broken), List.of()).write(report));
    }

    @Test
    void runnerClosesEveryTargetAndReportsOnlyMeasuredRun() throws Exception {
        AtomicInteger opened = new AtomicInteger(), closed = new AtomicInteger(), reported = new AtomicInteger();
        BenchmarkTargetFactory targets = digest -> {
            opened.incrementAndGet();
            var delegate = new BenchmarkTarget.InMemory(digest);
            return new BenchmarkTarget() {
                public void process(Command command) {
                    delegate.process(command);
                }

                public BookSnapshot snapshot() {
                    return delegate.snapshot();
                }

                public void close() {
                    closed.incrementAndGet();
                }
            };
        };
        var measurement = new PipelineBenchmark(new ProcessorFactory(ProcessorFactory.Kind.QUEUE, false), targets, new JvmMetrics());
        var report = new BenchmarkRunner(new MixedWorkload(), measurement, value -> reported.incrementAndGet()).run(options);
        assertEquals(2, opened.get());
        assertEquals(2, closed.get());
        assertEquals(1, reported.get());
        assertEquals(12, report.digest().count());
    }

    @Test
    void incorrectTargetClosesAndCannotPublishMeasurements() {
        AtomicInteger closed = new AtomicInteger(), reported = new AtomicInteger();
        BenchmarkTargetFactory targets = digest -> new BenchmarkTarget() {
            public void process(Command command) {
            }

            public BookSnapshot snapshot() {
                return new com.exchange.matching.OrderBook().snapshot();
            }

            public void close() {
                closed.incrementAndGet();
            }
        };
        var measurement = new PipelineBenchmark(new ProcessorFactory(ProcessorFactory.Kind.QUEUE, false), targets, new JvmMetrics());
        var runner = new BenchmarkRunner(new MixedWorkload(), measurement, value -> reported.incrementAndGet());
        assertThrows(IllegalStateException.class, () -> runner.run(options));
        assertEquals(1, closed.get());
        assertEquals(0, reported.get());
    }
}
