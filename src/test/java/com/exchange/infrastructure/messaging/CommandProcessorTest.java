package com.exchange.infrastructure.messaging;

import com.exchange.matching.OrderBook;
import com.exchange.matching.command.Command;
import com.exchange.application.port.CommandProcessor;
import com.exchange.bootstrap.ProcessorFactory;
import com.exchange.testsupport.TestWorkloads;
import com.exchange.testsupport.Events;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CommandProcessorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ParameterizedTest
    @EnumSource(ProcessorFactory.Kind.class)
    void wraparoundAndBackpressurePreserveExactlyTheSameEvents(ProcessorFactory.Kind kind) {
        var expected = new Events();
        var actual = new Events();
        var reference = new OrderBook();
        var consumer = new OrderBook();
        Command[] commands = TestWorkloads.cycle(40000);
        for (var c : commands) reference.process(c, expected);
        try (var pipeline = new ProcessorFactory(kind, false).create(16, s -> consumer.process(s.command(), actual))) {
            for (int i = 0; i < commands.length; i++)
                pipeline.submit(new CommandProcessor.Submission(commands[i], i, 0), TIMEOUT);
        }
        assertEquals(expected, actual);
        assertEquals(reference.snapshot(), consumer.snapshot());
        consumer.checkInvariants();
    }

    @ParameterizedTest
    @EnumSource(ProcessorFactory.Kind.class)
    void failureIsPropagatedAndShutdownDoesNotHang(ProcessorFactory.Kind kind) {
        var pipeline = new ProcessorFactory(kind, false).create(2, s -> {
            throw new IllegalArgumentException("injected");
        });
        pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), 0, 0), TIMEOUT);
        var error = assertThrows(IllegalStateException.class, () -> pipeline.awaitDrained(TIMEOUT));
        assertEquals("injected", error.getCause().getMessage());
        assertThrows(IllegalStateException.class, pipeline::close);
        assertThrows(IllegalStateException.class, () -> pipeline.submit(new CommandProcessor.Submission(Command.cancel(2), 0, 0), TIMEOUT));
    }

    @ParameterizedTest
    @EnumSource(ProcessorFactory.Kind.class)
    void publicationTimesOutWhenFullWithoutDroppingAcceptedCommands(ProcessorFactory.Kind kind) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        java.util.List<Integer> seen = new java.util.ArrayList<>();
        var pipeline = new ProcessorFactory(kind, false).create(2, s -> {
            entered.countDown();
            release.await();
            seen.add(s.index());
        });
        int accepted = 0;
        try {
            pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), accepted++, 0), TIMEOUT);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            // A queue releases a slot on take; a ring holds it until the handler returns.
            int additional = kind == ProcessorFactory.Kind.QUEUE ? 2 : 1;
            for (int i = 0; i < additional; i++)
                pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), accepted++, 0), TIMEOUT);
            assertThrows(IllegalStateException.class, () -> pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), 99, 0), Duration.ofMillis(20)));
        } finally {
            release.countDown();
            pipeline.close();
        }
        assertEquals(java.util.stream.IntStream.range(0, accepted).boxed().toList(), seen);
    }

    @Test
    void ownershipAndConfigurationAreEnforced() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ProcessorFactory(ProcessorFactory.Kind.QUEUE, false).create(3, s -> {
        }));
        try (var pipeline = new ProcessorFactory(ProcessorFactory.Kind.DISRUPTOR, true).create(8, s -> {
        })) {
            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread other = new Thread(() -> {
                try {
                    pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), 0, 0), TIMEOUT);
                } catch (Throwable ex) {
                    result.set(ex);
                }
            });
            other.start();
            other.join(5000);
            assertInstanceOf(IllegalStateException.class, result.get());
            pipeline.submit(new CommandProcessor.Submission(Command.cancel(1), 0, 0), TIMEOUT);
        }
    }
}
