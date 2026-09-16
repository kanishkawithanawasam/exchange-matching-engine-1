package com.exchange.benchmark;

import com.exchange.application.port.EventPublisher;

final class EventDigest implements EventPublisher {
    private long count;
    private long hash = 0xcbf29ce484222325L;

    public void onEvent(long seq, Type type, long id, long maker, long price, long qty, Reason reason) {
        mix(seq);
        mix(type.ordinal());
        mix(id);
        mix(maker);
        mix(price);
        mix(qty);
        mix(reason.ordinal());
        count++;
    }

    record Snapshot(long count, long hash) {
    }

    Snapshot snapshot() {
        return new Snapshot(count, hash);
    }

    private void mix(long value) {
        hash = (hash ^ value) * 0x100000001b3L;
    }
}
