package com.exchange.benchmark;

record BenchmarkReport(BenchmarkOptions options, BenchmarkResult result,
                       EventDigest.Snapshot digest, String workload) {
}
