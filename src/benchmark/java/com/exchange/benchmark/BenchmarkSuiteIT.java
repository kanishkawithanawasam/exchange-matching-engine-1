package com.exchange.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Maven orchestrates fresh JVMs; benchmark measurements never execute inside the JUnit JVM.
 */
class BenchmarkSuiteIT {
    @Test
    void measureTransportMatrixInSeparateProcesses() throws Exception {
        Path output = Path.of(System.getProperty("benchmark.output", "target/benchmark-results"));
        Files.createDirectories(output);
        new BenchmarkEnvironment(java.time.Clock.systemUTC(), Path.of("target/exchange.jar"), new LocalSystemCommands()).capture(output);
        int forks = Integer.getInteger("benchmark.forks", 3);
        assertTrue(forks > 0, "benchmark.forks must be positive");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        for (String rate : new String[]{"100000", "0"}) {
            String count = System.getProperty(rate.equals("0") ? "benchmark.saturationCount" : "benchmark.count",
                    rate.equals("0") ? "2000000" : "200000");
            for (int fork = 1; fork <= forks; fork++) {
                String[][] configurations = fork % 2 == 1
                        ? new String[][]{{"QUEUE", "blocking"}, {"DISRUPTOR", "blocking"}, {"DISRUPTOR", "yielding"}}
                        : new String[][]{{"DISRUPTOR", "yielding"}, {"DISRUPTOR", "blocking"}, {"QUEUE", "blocking"}};
                for (String[] configuration : configurations) {
                    String name = configuration[0] + "-" + configuration[1] + "-rate" + rate + "-fork" + fork;
                    Path csv = output.resolve(name + ".csv"), errors = output.resolve(name + ".stderr");
                    Process child = new ProcessBuilder(java, "-Xms512m", "-Xmx512m", "-XX:+UseG1GC",
                            "-cp", System.getProperty("java.class.path"), BenchmarkMain.class.getName(),
                            configuration[0], count, rate, "1024", configuration[1], "none",
                            System.getProperty("benchmark.warmups", "3"))
                            .redirectOutput(csv.toFile()).redirectError(errors.toFile()).start();
                    try {
                        assertTrue(child.waitFor(Long.getLong("benchmark.timeoutSeconds", 120L), TimeUnit.SECONDS), name + " timed out");
                        assertEquals(0, child.exitValue(), () -> name + " failed; see " + errors);
                        assertTrue(Files.readString(csv).contains("throughput_per_second,p50_ns,p99_ns,p999_ns"));
                    } finally {
                        if (child.isAlive()) {
                            child.destroyForcibly();
                            child.waitFor(5, TimeUnit.SECONDS);
                        }
                    }
                }
            }
        }
    }
}
