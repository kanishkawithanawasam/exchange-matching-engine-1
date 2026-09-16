package com.exchange.benchmark;

@FunctionalInterface
interface SystemCommands {
    String run(String... arguments) throws InterruptedException;
}
