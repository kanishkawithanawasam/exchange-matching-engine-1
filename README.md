# Exchange Matching Engine

This project was built to explore how an exchange matching engine handles price-time priority, durable command
processing, and recovery after a restart. It is a small Java 17 application for one instrument. Orders enter through a
bounded command pipeline, are recorded in a journal, and are then processed by a single matching thread.

The matching rules are straightforward: the best available price is matched first, and orders at the same price are
handled in the order they arrived. Trades execute at the resting order's price. The single-owner design keeps the
matching logic deterministic and makes the state easier to reason about and test.

This is a learning and portfolio project rather than a complete trading venue. It does not handle accounts,
authentication, risk checks, networking, replication, snapshots, or a durable outbound market-data feed.

## System features and capabilities

- Limit, market, and cancellation orders.
- FIFO matching within each price level, including partial fills.
- Market orders that expire if they cannot be filled immediately.
- Positive integer prices and quantities stored as `long` values.
- A versioned file journal with CRC32C checksums.
- Journal recovery with validation and replay.
- Blocking queue and LMAX Disruptor command processors.
- Immutable book snapshots and synchronous event callbacks.
- Tests for matching, recovery, corruption, failures, architecture boundaries, and randomized quantity conservation.
- An optional benchmark profile for comparing the two command processors.

## A small example

Suppose the book contains two sell orders:

```text
Order 1: sell 7 @ 101
Order 2: sell 5 @ 101
```

When a buy order for 9 arrives at 101, it trades with order 1 first and then trades for 2 units with order 2. Order 2
keeps its place at the front of the 101 price level with 3 units remaining.

The demo included with the project runs a similar sequence. It also cancels an order, lets a market order expire, closes
the journal, and opens it again to check that the recovered book matches the original state.

## Design

The code is split into four main areas:

```text
matching/          The order book, commands, models, and domain events
application/       Exchange lifecycle, recovery, and interfaces
infrastructure/    File journal and command-processor implementations
bootstrap/         Factories and the command-line application
```

`OrderBook` owns the mutable state. It uses sorted price indexes for bids and asks, a map for looking up orders by ID,
and a FIFO linked list at each price. The matching package does not know anything about files or queues. Those details
are provided through application ports and infrastructure implementations.

Each command is appended to the journal before it reaches the order book. In `SYNC` durability mode, the write is forced
to storage before the command is matched. On startup, the journal is checked and replayed into a new order book.
Rejected commands are journalled as well, so the command sequence and recovery behavior remain reproducible.

The matching core is not thread-safe. A processor owns the book and sends all commands to one consumer. A producer
should wait for the pipeline to drain before inspecting the snapshot or closing the exchange.

## Requirements

- Java 17 or newer
- A shell that can run the Maven Wrapper

The project has no database or external service dependencies. Maven 3.9.9 is downloaded by the wrapper the first time it
is used.

## Run

Build the project and run the demo:

```sh
./mvnw verify
java -jar target/exchange.jar demo
```

To recover an existing journal:

```sh
java -jar target/exchange.jar recover /path/to/orders.journal
```

The `recover` command takes the journal lock, validates the file, replays complete records, and truncates an incomplete
final frame. Since it can modify a partially written journal, it should be treated as a recovery operation rather than a
read-only inspection command.

## Using it in Java

The exchange can also be embedded in another application:

```java
import com.exchange.application.RecoveryService;
import com.exchange.application.port.EventPublisher;
import com.exchange.infrastructure.journal.FileCommandJournal;
import com.exchange.infrastructure.journal.FileJournalFactory;
import com.exchange.bootstrap.ExchangeFactory;
import com.exchange.matching.command.Command;
import com.exchange.matching.model.Side;

import java.nio.file.Path;

var exchanges = new ExchangeFactory(
        new RecoveryService(),
        new FileJournalFactory(
                Path.of("orders.journal"),
                FileCommandJournal.Durability.SYNC));

try (var exchange = exchanges.open(EventPublisher.DISCARD)) {
    exchange.process(Command.limit(1, Side.BUY, 100, 10));
    System.out.println(exchange.snapshot());
}
```

For asynchronous submission, create a `ProcessorFactory` and send immutable `CommandProcessor.Submission` values through
a blocking queue or Disruptor processor. An `EventPublisher` can be supplied when the application needs to receive
fills, cancellations, expiries, or rejection events. Event callbacks run synchronously and must not call back into the
exchange.

For a domain-only example, use `OrderBook.process(command, sink)`. Applications that need a different persistence
mechanism can provide their own `CommandJournal` implementation.

## Tests

Run the normal test suite with:

```sh
./mvnw test
```

The tests cover the order-book rules, cancellation from different positions in a price level, invalid commands,
integer-boundary values, journal replay, torn writes, checksum failures, compatibility with the original journal format,
processor ordering, backpressure, and fail-stop behavior. Randomized tests compare the production order book with an
independently implemented reference matcher.

The current evaluation has 35 passing tests with no failures or errors. See the [test specification](docs/TEST_SPECIFICATION.md)
for the expected behavior and acceptance criteria, and the [test evaluation](docs/TEST_EVALUATION.md) for the latest
test command, result breakdown, and limitations.

## Benchmarks

Benchmarks are kept separate from the normal application build. Run the full benchmark profile with:

```sh
./mvnw verify -Pbenchmarks
```

The results and environment details are written to `target/benchmark-results`. For a shorter smoke run:

```sh
./mvnw verify -Pbenchmarks \
  -Dbenchmark.count=8000 \
  -Dbenchmark.saturationCount=8000 \
  -Dbenchmark.forks=1 \
  -Dbenchmark.warmups=1
```

The benchmark code compares the blocking queue and Disruptor implementations using the same workload. It checks the
final state and event digest before reporting results. No historical performance numbers are included because results
depend heavily on the machine and JVM used.

## Continuous integration

The repository includes a `Jenkinsfile` for automated verification. A Jenkins build runs the Maven verification phase,
executes the recovery demo, publishes JUnit results, and archives the packaged application JAR. This gives each build a
repeatable check that the project still compiles, passes its tests, packages correctly, and can recover a journal.

The performance stage is optional and is enabled with the `RUN_PERFORMANCE` build parameter. It should run only on a
quiet, dedicated Jenkins agent with Java 17 or newer installed, because shared agents can produce misleading latency
results. The pipeline currently assumes a Unix-like agent because it uses the Maven Wrapper shell script.

## Additional Information

- [Architecture and recovery](docs/ARCHITECTURE.md)
- [Performance methodology](docs/PERFORMANCE.md)
- [Testing documentation](docs/TESTING.md)
- [Test specification](docs/TEST_SPECIFICATION.md)
- [Test evaluation](docs/TEST_EVALUATION.md)
