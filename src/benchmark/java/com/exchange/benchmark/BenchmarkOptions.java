package com.exchange.benchmark;

import com.exchange.bootstrap.ProcessorFactory;

import java.util.Locale;
import java.util.Set;

record BenchmarkOptions(ProcessorFactory.Kind kind, int count, long rate, int capacity,
                        String waitStrategy, String durability, int warmups) {
    BenchmarkOptions {
        if (kind == null || count < 8 || count % 8 != 0 || rate < 0 || rate > 1_000_000_000L
                || capacity < 2 || Integer.bitCount(capacity) != 1 || warmups < 0
                || !Set.of("blocking", "yielding").contains(waitStrategy)
                || !Set.of("none", "sync", "os_buffered").contains(durability)
                || (kind == ProcessorFactory.Kind.QUEUE && !waitStrategy.equals("blocking")))
            throw new IllegalArgumentException("count: positive multiple of 8; rate: 0..1e9; capacity: power of two >=2; "
                    + "wait: blocking/yielding (queue: blocking); durability: none/sync/os_buffered; warmups: >=0");
    }

    static BenchmarkOptions parse(String[] args) {
        if (args.length > 7) throw new IllegalArgumentException("kind count rate capacity wait durability warmups");
        return new BenchmarkOptions(
                ProcessorFactory.Kind.valueOf(arg(args, 0, "QUEUE").toUpperCase(Locale.ROOT)),
                Integer.parseInt(arg(args, 1, "200000")), Long.parseLong(arg(args, 2, "100000")),
                Integer.parseInt(arg(args, 3, "1024")), arg(args, 4, "blocking"),
                arg(args, 5, "none"), Integer.parseInt(arg(args, 6, "3")));
    }

    private static String arg(String[] args, int index, String fallback) {
        return index < args.length ? args[index] : fallback;
    }
}
