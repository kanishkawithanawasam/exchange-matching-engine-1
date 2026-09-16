package com.exchange.benchmark;

final class InMemoryTargetFactory implements BenchmarkTargetFactory {
    @Override
    public BenchmarkTarget open(EventDigest events) {
        return new BenchmarkTarget.InMemory(events);
    }
}
