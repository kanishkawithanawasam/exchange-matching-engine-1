package com.exchange.infrastructure.journal;

import com.exchange.matching.OrderBook;
import com.exchange.matching.command.*;
import com.exchange.matching.model.Side;
import com.exchange.testsupport.Events;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JournalRecordCodecTest {
    @Test
    void typedAndMalformedCommandsRetainIdenticalReplayBehaviour() throws Exception {
        var commands = Arrays.asList(
                Command.limit(1, Side.SELL, 100, 5), Command.market(2, Side.BUY, 2), Command.cancel(1),
                Command.limit(2, Side.BUY, 99, 3), Command.limit(3, null, 100, 2), Command.market(3, Side.BUY, -1),
                new MalformedCommand(null, 3, Side.BUY, 1, 1),
                new MalformedCommand(Command.Type.CANCEL, 1, Side.BUY, 0, 0),
                new MalformedCommand(Command.Type.MARKET, 3, Side.BUY, 10, 1), null);
        JournalRecordCodec codec = new JournalRecordCodec();
        OrderBook original = new OrderBook(), replay = new OrderBook();
        Events liveEvents = new Events(), replayEvents = new Events();
        ByteBuffer frame = ByteBuffer.allocate(JournalRecordCodec.RECORD_BYTES);
        long sequence = 0;
        for (Command command : commands) {
            codec.encode(command, ++sequence, frame);
            Command decoded = codec.decode(frame, sequence);
            original.process(command, liveEvents);
            replay.process(decoded, replayEvents);
            if (command != null) assertEquals(command, decoded);
        }
        assertEquals(original.snapshot(), replay.snapshot());
        assertEquals(liveEvents, replayEvents);
    }
}
