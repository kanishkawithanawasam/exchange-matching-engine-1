package com.exchange.benchmark;

import com.exchange.application.port.CommandProcessor;
import com.exchange.application.port.CommandProcessorFactory;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.BookSnapshot;

import java.time.Duration;
import java.util.Objects;

final class PipelineBenchmark {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private final CommandProcessorFactory processors;
    private final BenchmarkTargetFactory targets;
    private final JvmMetrics metrics;

    PipelineBenchmark(CommandProcessorFactory processors, BenchmarkTargetFactory targets, JvmMetrics metrics) {
        this.processors = Objects.requireNonNull(processors);
        this.targets = Objects.requireNonNull(targets);
        this.metrics = Objects.requireNonNull(metrics);
    }

    BenchmarkResult run(BenchmarkOptions options, Command[] commands, EventDigest.Snapshot expected,
                        BookSnapshot expectedState) throws Exception {
        long rate = options.rate();
        EventDigest actual = new EventDigest();
        long[] latencies = new long[commands.length];
        var gcBefore = metrics.garbageCollection();
        long elapsed;
        try (BenchmarkTarget target = targets.open(actual)) {
            try (var pipeline = processors.create(options.capacity(), submission -> {
                target.process(submission.command());
                latencies[submission.index()] = System.nanoTime() - submission.startedNanos();
            })) {
                long start = System.nanoTime();
                for (int i = 0; i < commands.length; i++) {
                    long scheduled = rate == 0 ? System.nanoTime() : start + (i * 1_000_000_000L / rate);
                    if (rate != 0) while (System.nanoTime() - scheduled < 0) Thread.onSpinWait();
                    pipeline.submit(new CommandProcessor.Submission(commands[i], i, scheduled), TIMEOUT);
                }
                pipeline.awaitDrained(TIMEOUT);
                elapsed = System.nanoTime() - start;
            }
            var state = target.snapshot();
            if (!expectedState.equals(state) || !actual.snapshot().equals(expected))
                throw new IllegalStateException("benchmark correctness mismatch");
        }
        var gcAfter = metrics.garbageCollection();
        return new BenchmarkResult(latencies, elapsed,
                gcAfter.count() - gcBefore.count(), gcAfter.millis() - gcBefore.millis());
    }
}
