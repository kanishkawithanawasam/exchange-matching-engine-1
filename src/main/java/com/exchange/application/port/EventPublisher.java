package com.exchange.application.port;

import com.exchange.matching.event.EventSink;

/**
 * Outbound application port. Called synchronously after journaling; must not reenter the exchange.
 */
@FunctionalInterface
public interface EventPublisher extends EventSink {
    EventPublisher DISCARD = (sequence, type, id, maker, price, quantity, reason) -> {
    };
}
