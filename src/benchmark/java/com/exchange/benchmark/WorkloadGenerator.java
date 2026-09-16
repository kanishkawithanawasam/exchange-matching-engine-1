package com.exchange.benchmark;

import com.exchange.matching.command.Command;

interface WorkloadGenerator {
    String name();

    Command[] generate(int count);
}
