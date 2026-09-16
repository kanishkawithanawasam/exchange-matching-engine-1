package com.exchange.matching;

import com.exchange.matching.PriceLevel.RestingOrderNode;
import com.exchange.matching.command.Command;
import com.exchange.matching.command.NewOrderCommand;
import com.exchange.matching.event.EventSink;
import com.exchange.matching.model.BookSnapshot;
import com.exchange.matching.model.RestingOrder;
import com.exchange.matching.model.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import static com.exchange.matching.command.Command.Type.*;
import static com.exchange.matching.event.EventSink.Reason.*;
import static com.exchange.matching.event.EventSink.Type.*;
import static com.exchange.matching.model.Side.BUY;

/**
 * Single-owner aggregate: coordinates matching, identity and price indexes; no IO or clocks.
 */
public final class OrderBook {
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final HashMap<Long, RestingOrderNode> orders = new HashMap<>();
    private long sequence;
    private long highestOrderId;
    private long tradeCount;

    public void process(Command command, EventSink sink) {
        Objects.requireNonNull(sink);
        sequence = Math.incrementExact(sequence);
        var reason = validate(command);
        if (reason != NONE) {
            emit(sink, REJECTED, command == null ? 0 : command.orderId(), 0, 0, 0, reason);
        } else if (command.type() == CANCEL) {
            cancel(command.orderId(), sink);
        } else {
            match((NewOrderCommand) command, sink);
        }
    }

    private void cancel(long id, EventSink sink) {
        RestingOrderNode order = orders.get(id);
        if (order == null) {
            emit(sink, REJECTED, id, 0, 0, 0, UNKNOWN_ORDER);
            return;
        }
        remove(order);
        emit(sink, CANCELLED, id, 0, order.level().price(), order.remaining(), NONE);
    }

    private void match(NewOrderCommand incoming, EventSink sink) {
        highestOrderId = incoming.orderId();
        emit(sink, ACCEPTED, incoming.orderId(), 0, incoming.price(), incoming.quantity(), NONE);
        long remaining = incoming.quantity();
        NavigableMap<Long, PriceLevel> opposite = incoming.side() == BUY ? asks : bids;
        while (remaining > 0 && !opposite.isEmpty()) {
            PriceLevel level = opposite.firstEntry().getValue();
            if (!incoming.crosses(level.price())) break;
            RestingOrderNode maker = level.firstOrder();
            long filled = Math.min(remaining, maker.remaining());
            maker.fill(filled);
            remaining -= filled;
            if (maker.isFilled()) remove(maker);
            tradeCount = Math.incrementExact(tradeCount);
            emit(sink, TRADE, incoming.orderId(), maker.id(), level.price(), filled, NONE);
        }
        if (remaining == 0) return;
        if (incoming.restsRemainder()) {
            PriceLevel level = side(incoming.side()).computeIfAbsent(incoming.price(),
                    price -> new PriceLevel(price, incoming.side()));
            orders.put(incoming.orderId(), level.append(incoming.orderId(), remaining));
        } else {
            emit(sink, EXPIRED, incoming.orderId(), 0, 0, remaining, NONE);
        }
    }

    // Validation order is part of the rejection/replay contract, including malformed historic input.
    private EventSink.Reason validate(Command command) {
        if (command == null || command.type() == null) return INVALID_COMMAND;
        if (command.orderId() <= 0) return INVALID_ID;
        if (command.type() == CANCEL)
            return command.side() == null && command.price() == 0 && command.quantity() == 0 ? NONE : INVALID_COMMAND;
        if (command.side() == null) return INVALID_COMMAND;
        if (command.orderId() <= highestOrderId) return INVALID_ID;
        if (command.quantity() <= 0) return INVALID_QUANTITY;
        if (command.type() == LIMIT ? command.price() <= 0 : command.price() != 0) return INVALID_PRICE;
        return NONE;
    }

    private NavigableMap<Long, PriceLevel> side(Side side) {
        return side == BUY ? bids : asks;
    }

    private void remove(RestingOrderNode order) {
        PriceLevel level = order.level();
        level.remove(order);
        orders.remove(order.id());
        if (level.isEmpty()) side(level.side()).remove(level.price());
    }

    private void emit(EventSink sink, EventSink.Type type, long id, long maker, long price, long quantity, EventSink.Reason reason) {
        sink.onEvent(sequence, type, id, maker, price, quantity, reason);
    }

    public long sequence() {
        return sequence;
    }

    /**
     * Observe only on the owner thread or after the processor has drained and stopped.
     */
    public BookSnapshot snapshot() {
        List<RestingOrder> result = new ArrayList<>(orders.size());
        for (var book : List.of(bids, asks))
            for (PriceLevel level : book.values()) level.appendSnapshotsTo(result);
        return new BookSnapshot(sequence, highestOrderId, tradeCount, result);
    }

    /**
     * Expensive diagnostics; not part of the measured matching path.
     */
    public void checkInvariants() {
        if (!bids.isEmpty() && !asks.isEmpty() && bids.firstKey() >= asks.firstKey())
            throw new IllegalStateException("crossed book");
        int count = 0;
        for (var book : List.of(bids, asks))
            for (PriceLevel level : book.values()) count += level.checkInvariants(orders);
        if (count != orders.size()) throw new IllegalStateException("index mismatch");
    }
}
