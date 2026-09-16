package com.exchange.benchmark;

import com.exchange.application.RecoveryService;
import com.exchange.bootstrap.ExchangeFactory;
import com.exchange.bootstrap.ProcessorFactory;
import com.exchange.infrastructure.journal.FileCommandJournal;
import com.exchange.infrastructure.journal.FileJournalFactory;

import java.io.PrintWriter;
import java.util.Locale;

/**
 * Only the composition root selects concrete workload, transport, persistence and output adapters.
 */
public final class BenchmarkMain {
    private BenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkOptions options = BenchmarkOptions.parse(args);
        JvmMetrics metrics = new JvmMetrics();
        BenchmarkTargetFactory targets;
        if (options.durability().equals("none")) {
            targets = new InMemoryTargetFactory();
        } else {
            var durability = FileCommandJournal.Durability.valueOf(options.durability().toUpperCase(Locale.ROOT));
            RecoveryService recovery = new RecoveryService();
            targets = new JournalledTargetFactory(path -> new ExchangeFactory(recovery, new FileJournalFactory(path, durability)));
        }
        var processors = new ProcessorFactory(options.kind(), options.waitStrategy().equals("yielding"));
        var measurement = new PipelineBenchmark(processors, targets, metrics);
        var reporter = new CsvReporter(new PrintWriter(System.out), metrics.description());
        new BenchmarkRunner(new MixedWorkload(), measurement, reporter).run(options);
    }
}
