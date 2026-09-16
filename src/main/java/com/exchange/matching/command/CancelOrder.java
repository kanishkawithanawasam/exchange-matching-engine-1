package com.exchange.matching.command;

import com.exchange.matching.model.Side;

public record CancelOrder(long orderId) implements Command {
    @Override
    public Type type() {
        return Type.CANCEL;
    }

    @Override
    public Side side() {
        return null;
    }

    @Override
    public long price() {
        return 0;
    }

    @Override
    public long quantity() {
        return 0;
    }
}
