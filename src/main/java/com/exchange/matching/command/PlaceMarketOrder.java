package com.exchange.matching.command;

import com.exchange.matching.model.Side;

public record PlaceMarketOrder(long orderId, Side side, long quantity) implements NewOrderCommand {
    @Override
    public Type type() {
        return Type.MARKET;
    }

    @Override
    public long price() {
        return 0;
    }

    @Override
    public boolean crosses(long makerPrice) {
        return true;
    }

    @Override
    public boolean restsRemainder() {
        return false;
    }
}
