package com.exchange.benchmark;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * JVM observations are gathered outside the per-command handler.
 */
final class JvmMetrics {
    record GarbageCollection(long count, long millis) {
    }

    GarbageCollection garbageCollection() {
        long count = 0, millis = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, bean.getCollectionCount());
            millis += Math.max(0, bean.getCollectionTime());
        }
        return new GarbageCollection(count, millis);
    }

    List<String> description() {
        return List.of(
                "java=" + System.getProperty("java.runtime.version") + "; vm=" + System.getProperty("java.vm.name"),
                "os=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
                        + "; processors=" + Runtime.getRuntime().availableProcessors() + "; max_heap_bytes=" + Runtime.getRuntime().maxMemory(),
                "jvm_args=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
    }
}
