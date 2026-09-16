package com.exchange.matching.command;

import com.exchange.matching.model.Side;

/**
 * Immutable input. Numeric validation happens in the book so rejections can be journalled.
 */
public sealed interface Command permits NewOrderCommand, CancelOrder, MalformedCommand {
    public enum Type {LIMIT, MARKET, CANCEL}

    Type type();

    long orderId();

    Side side();

    long price();

    long quantity();

    public static Command limit(long id, Side side, long price, long quantity) {
        return new PlaceLimitOrder(id, side, price, quantity);
    }

    public static Command market(long id, Side side, long quantity) {
        return new PlaceMarketOrder(id, side, quantity);
    }

    public static Command cancel(long id) {
        return new CancelOrder(id);
    }
}
