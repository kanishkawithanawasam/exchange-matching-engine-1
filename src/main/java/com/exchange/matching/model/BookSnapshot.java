package com.exchange.matching.model;

import java.util.List;

public record BookSnapshot(long sequence, long highestOrderId, long tradeCount, List<RestingOrder> orders) {
    public BookSnapshot {
        orders = List.copyOf(orders);
    }
}
