package com.exchange.benchmark;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes to a supplied destination; owns no global output stream or benchmark execution.
 */
final class CsvReporter implements BenchmarkReporter {
    private final PrintWriter output;
    private final List<String> environment;

    CsvReporter(PrintWriter output, List<String> environment) {
        this.output = Objects.requireNonNull(output);
        this.environment = List.copyOf(environment);
    }

    @Override
    public void write(BenchmarkReport report) {
        BenchmarkOptions options = report.options();
        BenchmarkResult result = report.result();
        for (String line : environment) output.println("# " + line);
        output.println("# workload=" + report.workload()
                + "; boundary=before-publication-to-after-matching-and-digest; rate=0 uses producer arrival, otherwise scheduled arrival; gc metrics include pipeline startup/shutdown");
        output.println("kind,wait,durability,commands,offered_per_second,capacity,warmups,throughput_per_second,p50_ns,p99_ns,p999_ns,max_ns,gc_count,gc_ms,event_count,event_digest");
        output.printf(Locale.ROOT, "%s,%s,%s,%d,%d,%d,%d,%.2f,%d,%d,%d,%d,%d,%d,%d,%d%n",
                options.kind(), options.waitStrategy(), options.durability(), options.count(), options.rate(), options.capacity(), options.warmups(),
                options.count() * 1e9 / result.elapsed(), result.percentile(.50), result.percentile(.99), result.percentile(.999),
                result.maximum(), result.gcCount(), result.gcMillis(), report.digest().count(), report.digest().hash());
        output.flush();
        if (output.checkError()) throw new IllegalStateException("benchmark report could not be written");
    }
}
