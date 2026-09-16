package com.exchange.matching;

import com.exchange.matching.model.RestingOrder;
import com.exchange.matching.model.Side;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns FIFO membership and all link mutations for one price and side.
 */
final class PriceLevel {
    private final long price;
    private final Side side;
    private RestingOrderNode head;
    private RestingOrderNode tail;

    PriceLevel(long price, Side side) {
        if (price <= 0) throw new IllegalArgumentException("price must be positive");
        this.price = price;
        this.side = Objects.requireNonNull(side);
    }

    long price() {
        return price;
    }

    Side side() {
        return side;
    }

    boolean isEmpty() {
        return head == null;
    }

    RestingOrderNode firstOrder() {
        if (head == null) throw new IllegalStateException("price level is empty");
        return head;
    }

    RestingOrderNode append(long id, long quantity) {
        if (id <= 0 || quantity <= 0 || (tail != null && id <= tail.id))
            throw new IllegalArgumentException("positive quantity and increasing order IDs required");
        RestingOrderNode order = new RestingOrderNode(id, quantity, this);
        order.previous = tail;
        if (tail == null) head = order;
        else tail.next = order;
        tail = order;
        return order;
    }

    void remove(RestingOrderNode order) {
        if (order.level != this || !order.linked)
            throw new IllegalArgumentException("order is not a member of this level");
        if (order.previous == null) head = order.next;
        else order.previous.next = order.next;
        if (order.next == null) tail = order.previous;
        else order.next.previous = order.previous;
        order.previous = null;
        order.next = null;
        order.linked = false;
    }

    void appendSnapshotsTo(List<RestingOrder> target) {
        for (RestingOrderNode order = head; order != null; order = order.next)
            target.add(new RestingOrder(order.id, side, price, order.remaining));
    }

    int checkInvariants(Map<Long, RestingOrderNode> index) {
        if (head == null || tail == null) throw new IllegalStateException("empty indexed level");
        int count = 0;
        RestingOrderNode previous = null;
        for (RestingOrderNode order = head; order != null; order = order.next) {
            if (++count > index.size() || order.previous != previous || order.level != this || !order.linked
                    || order.remaining <= 0 || index.get(order.id) != order
                    || (previous != null && previous.id >= order.id))
                throw new IllegalStateException("broken order linkage/priority");
            previous = order;
        }
        if (previous != tail) throw new IllegalStateException("broken tail");
        return count;
    }

    /**
     * Static nesting scopes the type; every node has independent state and an explicit level reference.
     */
    static final class RestingOrderNode {
        private final long id;
        private final PriceLevel level;
        private long remaining;
        private RestingOrderNode previous;
        private RestingOrderNode next;
        private boolean linked = true;

        private RestingOrderNode(long id, long remaining, PriceLevel level) {
            this.id = id;
            this.remaining = remaining;
            this.level = level;
        }

        long id() {
            return id;
        }

        long remaining() {
            return remaining;
        }

        PriceLevel level() {
            return level;
        }

        boolean isFilled() {
            return remaining == 0;
        }

        void fill(long quantity) {
            if (!linked || quantity <= 0 || quantity > remaining)
                throw new IllegalArgumentException("fill must be positive and cannot exceed a resting order's remainder");
            remaining -= quantity;
        }
    }
}
