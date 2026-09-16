package com.exchange.benchmark;

import java.util.Arrays;

/**
 * Owns immutable measurements. Copying and sorting happen after the timed interval.
 */
final class BenchmarkResult {
    private final long[] latencies;
    private final long elapsed;
    private final long gcCount;
    private final long gcMillis;

    BenchmarkResult(long[] samples, long elapsed, long gcCount, long gcMillis) {
        if (samples.length == 0 || elapsed <= 0)
            throw new IllegalArgumentException("nonempty samples and positive duration required");
        latencies = samples.clone();
        Arrays.sort(latencies);
        this.elapsed = elapsed;
        this.gcCount = gcCount;
        this.gcMillis = gcMillis;
    }

    long elapsed() {
        return elapsed;
    }

    long gcCount() {
        return gcCount;
    }

    long gcMillis() {
        return gcMillis;
    }

    int sampleCount() {
        return latencies.length;
    }

    long maximum() {
        return latencies[latencies.length - 1];
    }

    long percentile(double p) {
        if (!(p > 0 && p <= 1)) throw new IllegalArgumentException("percentile must be in (0, 1]");
        return latencies[(int) Math.ceil(latencies.length * p) - 1];
    }
}
