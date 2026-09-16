package com.exchange.testsupport;

import com.exchange.application.port.EventPublisher;
import com.exchange.matching.event.EventSink;

import java.util.ArrayList;
import java.util.List;

public final class Events extends ArrayList<Events.Event> implements EventPublisher {
    public record Event(long sequence, EventSink.Type type, long id, long maker, long price, long qty,
                        EventSink.Reason reason) {
    }

    public void onEvent(long sequence, Type type, long id, long maker, long price, long qty, Reason reason) {
        add(new Event(sequence, type, id, maker, price, qty, reason));
    }

    public List<Event> trades() {
        return stream().filter(event -> event.type() == Type.TRADE).toList();
    }
}
