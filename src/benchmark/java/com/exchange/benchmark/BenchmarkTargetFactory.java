package com.exchange.benchmark;

import java.io.IOException;

@FunctionalInterface
interface BenchmarkTargetFactory {
    BenchmarkTarget open(EventDigest events) throws IOException;
}
