package com.exchange.benchmark;

import com.exchange.matching.command.Command;
import com.exchange.matching.model.Side;

final class MixedWorkload implements WorkloadGenerator {
    @Override
    public String name() {
        return "v1-eight-command-cycle";
    }

    @Override
    public Command[] generate(int count) {
        if (count < 8 || count % 8 != 0)
            throw new IllegalArgumentException("count must be a positive multiple of eight");
        Command[] result = new Command[count];
        for (int i = 0; i < count; i += 8) {
            long id = i + 1L;
            result[i] = Command.limit(id, Side.SELL, 101, 10);
            result[i + 1] = Command.limit(id + 1, Side.SELL, 102, 10);
            result[i + 2] = Command.limit(id + 2, Side.BUY, 101, 6);
            result[i + 3] = Command.market(id + 3, Side.BUY, 8);
            result[i + 4] = Command.cancel(id + 1);
            result[i + 5] = Command.limit(id + 5, Side.BUY, 99, 7);
            result[i + 6] = Command.market(id + 6, Side.SELL, 3);
            result[i + 7] = Command.cancel(id + 5);
        }
        return result;
    }
}
