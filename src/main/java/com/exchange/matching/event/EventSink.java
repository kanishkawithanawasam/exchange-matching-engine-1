package com.exchange.matching.event;

/**
 * Called synchronously on the engine owner thread; must not throw or reenter the engine.
 */
@FunctionalInterface
public interface EventSink {
    enum Type {ACCEPTED, TRADE, CANCELLED, EXPIRED, REJECTED}

    enum Reason {NONE, INVALID_COMMAND, INVALID_ID, INVALID_PRICE, INVALID_QUANTITY, UNKNOWN_ORDER}

    EventSink DISCARD = (sequence, type, orderId, makerId, price, quantity, reason) -> {
    };

    /**
     * TRADE identifies the taker in orderId and resting maker in makerId.
     */
    void onEvent(long sequence, Type type, long orderId, long makerId,
                 long price, long quantity, Reason reason);
}
