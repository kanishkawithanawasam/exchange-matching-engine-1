package com.exchange.matching;

import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.matching.command.MalformedCommand;
import com.exchange.matching.model.RestingOrder;
import com.exchange.testsupport.Events;
import com.exchange.testsupport.Events.Event;


import org.junit.jupiter.api.Test;

import java.util.*;

import static com.exchange.matching.model.Side.*;
import static com.exchange.matching.event.EventSink.Type.*;
import static com.exchange.matching.event.EventSink.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    @Test
    void priceThenFifoWithMakerPricesAndPartialFills() {
        Events events = new Events();
        var engine = new OrderBook();
        engine.process(Command.limit(1, SELL, 102, 4), events);
        engine.process(Command.limit(2, SELL, 101, 5), events);
        engine.process(Command.limit(3, SELL, 101, 7), events);
        engine.process(Command.limit(4, BUY, 103, 14), events);
        assertEquals(List.of(new Event(4, TRADE, 4, 2, 101, 5, NONE), new Event(4, TRADE, 4, 3, 101, 7, NONE),
                new Event(4, TRADE, 4, 1, 102, 2, NONE)), events.trades());
        assertEquals(List.of(new RestingOrder(1, SELL, 102, 2)), engine.snapshot().orders());
        engine.checkInvariants();
    }

    @Test
    void bidPriorityAndLimitRemainderRest() {
        Events events = new Events();
        var engine = new OrderBook();
        engine.process(Command.limit(1, BUY, 99, 3), events);
        engine.process(Command.limit(2, BUY, 100, 4), events);
        engine.process(Command.limit(3, SELL, 99, 10), events);
        assertEquals(List.of(2L, 1L), events.trades().stream().map(Event::maker).toList());
        assertEquals(List.of(new RestingOrder(3, SELL, 99, 3)), engine.snapshot().orders());
    }

    @Test
    void cancelsHeadMiddleTailAndLastWithoutLosingPriority() {
        for (int cancelled = 1; cancelled <= 4; cancelled++) {
            Events events = new Events();
            var engine = new OrderBook();
            for (int id = 1; id <= 4; id++) engine.process(Command.limit(id, BUY, 100, 2), events);
            engine.process(Command.cancel(cancelled), events);
            engine.checkInvariants();
            engine.process(Command.market(5, SELL, 20), events);
            engine.checkInvariants();
            int removed = cancelled;
            assertEquals(java.util.stream.LongStream.rangeClosed(1, 4).filter(id -> id != removed).boxed().toList(),
                    events.trades().stream().map(Event::maker).toList());
            assertTrue(engine.snapshot().orders().isEmpty());
            assertEquals(14, events.get(events.size() - 1).qty());
        }
    }

    @Test
    void emptyMarketExpiresAndInvalidCommandsDoNotChangeBookOrReserveIds() {
        Events events = new Events();
        var engine = new OrderBook();
        engine.process(Command.market(1, BUY, 5), events);
        assertEquals(EXPIRED, events.get(1).type());
        engine.process(Command.limit(2, BUY, 0, 5), events);
        engine.process(Command.limit(2, null, 1, 5), events);
        engine.process(Command.limit(2, BUY, 1, 0), events);
        engine.process(Command.limit(0, BUY, 1, 5), events);
        engine.process(null, events);
        engine.process(new MalformedCommand(null, 3, BUY, 1, 1), events);
        engine.process(new MalformedCommand(Command.Type.CANCEL, 1, BUY, 0, 0), events);
        engine.process(new MalformedCommand(Command.Type.MARKET, 2, BUY, 10, 1), events);
        engine.process(Command.cancel(999), events);
        engine.process(Command.limit(2, BUY, 1, 5), events);
        engine.process(Command.cancel(2), events);
        engine.process(Command.limit(2, BUY, 1, 5), events);
        assertEquals(INVALID_ID, events.get(events.size() - 1).reason());
        assertTrue(engine.snapshot().orders().isEmpty());
        assertEquals(2, engine.snapshot().highestOrderId());
        assertEquals(10, events.stream().filter(e -> e.type() == REJECTED).count());
    }

    @Test
    void maximumLongValuesMatchWithoutQuantityOrPriceOverflow() {
        Events events = new Events();
        var engine = new OrderBook();
        engine.process(Command.limit(1, SELL, Long.MAX_VALUE, Long.MAX_VALUE), events);
        engine.process(Command.limit(Long.MAX_VALUE, BUY, Long.MAX_VALUE, Long.MAX_VALUE), events);
        assertEquals(Long.MAX_VALUE, events.trades().get(0).qty());
        assertTrue(engine.snapshot().orders().isEmpty());
        engine.checkInvariants();
    }

    @Test
    void randomizedStreamsAgreeWithIndependentListMatcherAndConserveEveryOrder() {
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            Events events = new Events();
            var engine = new OrderBook();
            Reference reference = new Reference();
            Map<Long, Long> original = new HashMap<>(), executed = new HashMap<>(), removed = new HashMap<>();
            for (long id = 1; id <= 2000; id++) {
                var side = random.nextBoolean() ? BUY : SELL;
                long quantity = random.nextInt(100) + 1;
                Command c = switch (random.nextInt(5)) {
                    case 0 -> Command.cancel(1 + random.nextLong(id));
                    case 1 -> Command.market(id, side, quantity);
                    default -> Command.limit(id, side, 95 + random.nextInt(11), quantity);
                };
                if (c.type() != Command.Type.CANCEL) original.put(id, quantity);
                int offset = events.size();
                engine.process(c, events);
                reference.process(c);
                engine.checkInvariants();
                assertEquals(reference.events, events.subList(offset, events.size()), "seed=" + seed + " id=" + id);
                assertEquals(reference.snapshot(), engine.snapshot().orders(), "seed=" + seed + " id=" + id);
                for (Event event : reference.events)
                    if (event.type() == TRADE) {
                        executed.merge(event.id(), event.qty(), Math::addExact);
                        executed.merge(event.maker(), event.qty(), Math::addExact);
                        assertTrue(executed.get(event.id()) <= original.get(event.id()));
                        assertTrue(executed.get(event.maker()) <= original.get(event.maker()));
                    }
                for (Event event : reference.events)
                    if (event.type() == CANCELLED || event.type() == EXPIRED)
                        removed.merge(event.id(), event.qty(), Math::addExact);
                for (var resting : engine.snapshot().orders())
                    assertEquals(original.get(resting.id()).longValue(),
                            resting.remaining() + executed.getOrDefault(resting.id(), 0L));
            }
            for (var resting : engine.snapshot().orders())
                removed.merge(resting.id(), resting.remaining(), Math::addExact);
            original.forEach((id, qty) -> assertEquals(qty.longValue(), executed.getOrDefault(id, 0L) + removed.getOrDefault(id, 0L)));
        }
    }

    /**
     * Deliberately slow list scans and sorting, sharing no matching/index code with production.
     */
    static final class Reference {
        final List<RestingOrder> resting = new ArrayList<>();
        final Events events = new Events();
        long seq;

        void process(Command c) {
            events.clear();
            seq++;
            if (c.type() == Command.Type.CANCEL) {
                var found = resting.stream().filter(o -> o.id() == c.orderId()).findFirst();
                if (found.isEmpty()) events.onEvent(seq, REJECTED, c.orderId(), 0, 0, 0, UNKNOWN_ORDER);
                else {
                    var o = found.get();
                    resting.remove(o);
                    events.onEvent(seq, CANCELLED, o.id(), 0, o.price(), o.remaining(), NONE);
                }
                return;
            }
            events.onEvent(seq, ACCEPTED, c.orderId(), 0, c.price(), c.quantity(), NONE);
            long left = c.quantity();
            while (left > 0) {
                Comparator<RestingOrder> prices = Comparator.comparingLong(RestingOrder::price);
                if (c.side() == SELL) prices = prices.reversed();
                var candidate = resting.stream().filter(o -> o.side() != c.side())
                        .filter(o -> c.type() == Command.Type.MARKET || (c.side() == BUY ? o.price() <= c.price() : o.price() >= c.price()))
                        .min(prices.thenComparingLong(RestingOrder::id));
                if (candidate.isEmpty()) break;
                var o = candidate.get();
                long qty = Math.min(left, o.remaining());
                left -= qty;
                resting.remove(o);
                if (o.remaining() > qty)
                    resting.add(new RestingOrder(o.id(), o.side(), o.price(), o.remaining() - qty));
                events.onEvent(seq, TRADE, c.orderId(), o.id(), o.price(), qty, NONE);
            }
            if (left > 0) {
                if (c.type() == Command.Type.MARKET) events.onEvent(seq, EXPIRED, c.orderId(), 0, 0, left, NONE);
                else resting.add(new RestingOrder(c.orderId(), c.side(), c.price(), left));
            }
        }

        List<RestingOrder> snapshot() {
            return resting.stream().sorted(Comparator.comparing(RestingOrder::side)
                    .thenComparing((a, b) -> a.side() == BUY ? Long.compare(b.price(), a.price()) : Long.compare(a.price(), b.price()))
                    .thenComparingLong(RestingOrder::id)).toList();
        }
    }
}
