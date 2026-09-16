package com.exchange.benchmark;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Captures provenance before any timed work. Restricted hardware queries are labelled unavailable.
 */
final class BenchmarkEnvironment {
    private final java.time.Clock clock;
    private final Path artifact;
    private final SystemCommands commands;

    BenchmarkEnvironment(java.time.Clock clock, Path artifact, SystemCommands commands) {
        this.clock = java.util.Objects.requireNonNull(clock);
        this.artifact = java.util.Objects.requireNonNull(artifact);
        this.commands = java.util.Objects.requireNonNull(commands);
    }

    void capture(Path output) throws Exception {
        StringBuilder metadata = new StringBuilder("captured=").append(clock.instant()).append('\n')
                .append("java=").append(System.getProperty("java.runtime.version")).append('\n')
                .append("os=").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n')
                .append("processors=").append(Runtime.getRuntime().availableProcessors()).append('\n');
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean)
            metadata.append("physical_memory_bytes=").append(bean.getTotalMemorySize()).append('\n');
        if (System.getProperty("os.name").startsWith("Mac"))
            metadata.append(commands.run("sysctl", "machdep.cpu.brand_string", "hw.memsize", "hw.ncpu"));
        else if (Files.isReadable(Path.of("/proc/cpuinfo"))) {
            try (var lines = Files.lines(Path.of("/proc/cpuinfo"))) {
                lines.filter(line -> line.startsWith("model name") || line.startsWith("Hardware"))
                        .distinct().limit(4).forEach(line -> metadata.append(line).append('\n'));
            }
        }
        metadata.append("revision=").append(commands.run("git", "rev-parse", "--verify", "HEAD"))
                .append("worktree_status=\n").append(commands.run("git", "status", "--short"));
        metadata.append("exchange_jar_sha256=").append(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)))).append('\n');
        Files.writeString(output.resolve("environment.txt"), metadata);
    }

}
