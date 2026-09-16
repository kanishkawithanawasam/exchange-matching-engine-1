package com.exchange.matching.command;

import com.exchange.matching.model.Side;

public record PlaceLimitOrder(long orderId, Side side, long price, long quantity) implements NewOrderCommand {
    @Override
    public Type type() {
        return Type.LIMIT;
    }

    @Override
    public boolean crosses(long makerPrice) {
        return side == Side.BUY ? price >= makerPrice : price <= makerPrice;
    }

    @Override
    public boolean restsRemainder() {
        return true;
    }
}
