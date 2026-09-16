package com.exchange.matching;

import com.exchange.matching.model.RestingOrder;
import com.exchange.matching.model.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriceLevelTest {
    @Test
    void orderRejectsInvalidFillsWithoutChangingItsQuantity() {
        PriceLevel level = new PriceLevel(100, Side.BUY);
        var order = level.append(1, 10);
        for (long invalid : new long[]{0, -1, 11, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> order.fill(invalid));
            assertEquals(10, order.remaining());
        }
        order.fill(4);
        assertEquals(6, order.remaining());
        assertFalse(order.isFilled());
        order.fill(6);
        assertTrue(order.isFilled());
        assertThrows(IllegalArgumentException.class, () -> order.fill(1));
    }

    @Test
    void onlyTheOwningLevelCanRemoveAnOrderAndRemovalCannotRepeat() {
        PriceLevel owning = new PriceLevel(100, Side.BUY), other = new PriceLevel(101, Side.BUY);
        var order = owning.append(1, 10);
        assertThrows(IllegalArgumentException.class, () -> other.remove(order));
        assertSame(order, owning.firstOrder());
        assertTrue(other.isEmpty());
        owning.remove(order);
        assertTrue(owning.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> owning.remove(order));
        assertThrows(IllegalArgumentException.class, () -> order.fill(1));
        assertEquals(10, order.remaining());
    }

    @Test
    void levelProtectsFifoAndCanAppendAfterRemovingTheTail() {
        PriceLevel level = new PriceLevel(100, Side.SELL);
        var first = level.append(1, 10);
        var second = level.append(2, 20);
        assertThrows(IllegalArgumentException.class, () -> level.append(1, 5));
        assertThrows(IllegalArgumentException.class, () -> level.append(3, 0));
        first.fill(3);
        level.remove(second);
        var third = level.append(3, 30);
        assertSame(first, level.firstOrder());
        assertEquals(2, level.checkInvariants(Map.of(1L, first, 3L, third)));
        List<RestingOrder> snapshots = new ArrayList<>();
        level.appendSnapshotsTo(snapshots);
        assertEquals(List.of(new RestingOrder(1, Side.SELL, 100, 7), new RestingOrder(3, Side.SELL, 100, 30)), snapshots);
        level.remove(first);
        assertSame(third, level.firstOrder());
    }
}
