package com.exchange.benchmark;

import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;

import java.util.Objects;

/**
 * Coordinates a replaceable workload, measurement and reporter; owns no transport or output selection.
 */
final class BenchmarkRunner {
    private final WorkloadGenerator workload;
    private final PipelineBenchmark measurement;
    private final BenchmarkReporter reporter;

    BenchmarkRunner(WorkloadGenerator workload, PipelineBenchmark measurement, BenchmarkReporter reporter) {
        this.workload = Objects.requireNonNull(workload);
        this.measurement = Objects.requireNonNull(measurement);
        this.reporter = Objects.requireNonNull(reporter);
    }

    BenchmarkReport run(BenchmarkOptions options) throws Exception {
        Command[] commands = workload.generate(options.count());
        if (commands.length != options.count())
            throw new IllegalArgumentException("workload size does not match options");
        EventDigest expected = new EventDigest();
        OrderBook reference = new OrderBook();
        for (Command command : commands) reference.process(command, expected);
        var state = reference.snapshot();
        for (int i = 0; i < options.warmups(); i++) measurement.run(options, commands, expected.snapshot(), state);
        BenchmarkResult result = measurement.run(options, commands, expected.snapshot(), state);
        BenchmarkReport report = new BenchmarkReport(options, result, expected.snapshot(), workload.name());
        reporter.write(report);
        return report;
    }
}
