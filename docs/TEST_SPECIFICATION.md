# Test specification

## 1. Purpose

This document defines what must be tested in the exchange matching engine and the conditions for accepting the
implementation. It describes expected behavior before discussing test results.

The system is a single-instrument exchange engine with four main boundaries:

1. The matching domain accepts typed commands and updates one order book.
2. The application service records commands before matching and manages recovery.
3. The journal adapter writes and replays versioned command frames.
4. The command processor transfers submissions from a producer to the single owner of the exchange.

## 2. Functional requirements

The implementation must satisfy the following rules:

- A better price has priority over a worse price.
- Orders at the same price are matched in FIFO order.
- Trades execute at the resting maker's price.
- A partially filled resting order keeps its original priority.
- Limit orders may rest in the book.
- Market orders never rest; remaining quantity expires immediately.
- Cancellation removes only the remaining quantity.
- Invalid, unknown, completed, or cancelled orders produce rejection events.
- Every command advances the command sequence, including rejected commands.
- Accepted new order IDs are positive and strictly increasing.
- Rejected new orders and cancellations do not reserve a new order ID.
- A journal append completes before matching begins.
- Recovery replays commands into a new book and reproduces the same state and events.
- A journal or event-processing failure prevents unsafe reuse of the exchange.
- A processor preserves publication order and reports backpressure and consumer failure.

## 3. Test levels

### 3.1 Unit tests

Unit tests cover `PriceLevel`, `OrderBook`, command validation, journal frame encoding, and individual lifecycle rules.
These tests should isolate one behavior at a time and make failures easy to diagnose.

### 3.2 Component tests

Component tests combine the application service with in-memory journals, event publishers, and processor
implementations. They verify ordering between journal, matching, and event publication without requiring external
services.

### 3.3 Integration tests

Integration tests use the file journal and packaged application. They cover replay after restart, file locking,
incomplete writes, corruption, compatibility with journal format v1, and abrupt process termination.

### 3.4 Architecture tests

Architecture tests inspect production dependencies. The matching domain must not depend on application, infrastructure,
bootstrap, filesystem, or concurrency code. Application services may depend on ports but must not select concrete
adapters. Benchmark code must not become part of the production artifact.

### 3.5 Randomized tests

Randomized tests use deterministic seeds and an independent reference matcher. The reference implementation uses list
scans and sorting, rather than the production tree and linked lists. After every generated command, the production and
reference states are compared.

## 4. Acceptance criteria

| Area                 | Acceptance condition                                                                                         |
|----------------------|--------------------------------------------------------------------------------------------------------------|
| Matching             | Best-price, FIFO, maker-price, partial-fill, cancellation, and market-expiry cases pass.                     |
| Quantity             | Executed + cancelled + expired + resting quantity equals the original quantity for every order.              |
| Validation           | Invalid prices, quantities, sides, IDs, and command shapes are rejected as specified.                        |
| Integer safety       | Valid values near `Long.MAX_VALUE` do not overflow or corrupt book state.                                    |
| Replay               | Final snapshots and complete event streams match before and after recovery.                                  |
| Journal integrity    | Bad headers, versions, checksums, sequence numbers, and wire codes are rejected.                             |
| Torn writes          | Every incomplete final-frame length follows the recovery rule and a valid journal can be extended afterward. |
| Lifecycle            | Journals and processors close correctly after successful and failed startup.                                 |
| Failure handling     | Failed appends prevent matching; handler failures poison the exchange and are reported.                      |
| Concurrency boundary | Queue and Disruptor processors preserve ordering, visibility, capacity, timeout, drain, and ownership rules. |
| Architecture         | ArchUnit dependency rules pass.                                                                              |
| Artifact             | Test and benchmark classes are absent from the application JAR.                                              |

## 5. Invariants

After every command, the order book must satisfy these invariants:

- No bid crosses the best ask after processing is complete.
- Every resting order has a positive remaining quantity.
- Every resting order appears exactly once in its price-level FIFO list.
- Every indexed order is present in the corresponding price level.
- No detached order remains reachable from a price-level list or ID index.
- A filled, cancelled, or expired order has no remaining resting quantity.
- Event quantities never exceed the available order quantity.

## 6. Required evidence

A test run should record:

- source revision;
- Java and Maven versions;
- command used;
- tests run, failures, errors, and skips;
- benchmark settings when the benchmark profile is used;
- generated result files for integration or performance runs.

The test specification does not define a performance threshold. Throughput and latency are specified separately
in [PERFORMANCE.md](PERFORMANCE.md).
