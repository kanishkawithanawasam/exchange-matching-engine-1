package com.exchange.benchmark;

@FunctionalInterface
interface BenchmarkReporter {
    void write(BenchmarkReport report);
}
