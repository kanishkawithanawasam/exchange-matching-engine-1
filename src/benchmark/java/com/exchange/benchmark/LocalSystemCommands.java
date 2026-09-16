package com.exchange.benchmark;

import java.util.concurrent.TimeUnit;

final class LocalSystemCommands implements SystemCommands {
    @Override
    public String run(String... args) throws InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
        } catch (java.io.IOException | SecurityException error) {
            return "unavailable: " + error.getMessage() + '\n';
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) return "unavailable: query timed out\n";
            if (process.exitValue() != 0) return "unavailable: " + String.join(" ", args) + '\n';
            return new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            return "unavailable: " + error.getMessage() + '\n';
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
