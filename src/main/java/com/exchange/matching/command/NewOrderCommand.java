package com.exchange.matching.command;

/**
 * Execution policy of a validated incoming order.
 */
public sealed interface NewOrderCommand extends Command permits PlaceLimitOrder, PlaceMarketOrder {
    boolean crosses(long makerPrice);

    boolean restsRemainder();
}
