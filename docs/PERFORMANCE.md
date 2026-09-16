# Performance specification and report

## Purpose

This document defines what performance means for this project and how it should be measured. It describes the
measurement boundaries before presenting results, so a number can be understood and repeated rather than treated as a
general claim about the engine.

The main comparison is between the two bounded command processors:

- the blocking-queue processor;
- the LMAX Disruptor processor, using blocking or yielding waits.

Both processors must run the same immutable workload against the same matching core. The benchmark is intended to show
the cost and behavior of the pipeline choices. It is not intended to establish production-trading latency.

## Performance specification

The system under test has one producer, one processor consumer, one exchange, and one instrument. The processor
serializes commands before they reach the order book. A command is considered complete when the consumer has finished
journaling, matching, and invoking the event digest callback.

The benchmark workload is an eight-command cycle:

1. Sell 10 at 101.
2. Sell 10 at 102.
3. Buy 6 at 101.
4. Buy 8 at market.
5. Cancel the second sell order's remaining quantity.
6. Buy 7 at 99.
7. Sell 3 at market.
8. Cancel the remaining buy quantity.

Each cycle ends with an empty book. It produces 12 events, including four trades. New order IDs increase strictly
throughout the run.

The benchmark supports two load models:

- **Fixed rate:** the producer schedules arrivals at 100,000 commands per second. If it falls behind, the original
  scheduled arrival time is retained, so queueing and producer stalls remain visible.
- **Saturation:** a rate of `0` makes the producer publish as quickly as the bounded processor allows. This is a
  closed-loop capacity test; its latency is not an open-loop overload measurement.

## Performance criteria

Every benchmark run must satisfy these criteria before its measurements are accepted:

| Criterion              | Required evidence                                                                                                                  |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Functional correctness | Final book state, event count, and event digest match an untimed synchronous reference run.                                        |
| Command delivery       | No command is silently dropped. Published commands are processed in order.                                                         |
| Latency definition     | Start and end timestamps use the boundaries described above and are recorded in the output metadata.                               |
| Throughput definition  | The report states the command count, elapsed interval, and whether the run was fixed-rate or saturated.                            |
| Repeatability          | Results come from multiple fresh JVM forks and warmups, with the machine and JVM configuration recorded.                           |
| Tail visibility        | p50, p99, and p99.9 are reported per run; averages must not hide tail outliers.                                                    |
| Durability clarity     | `SYNC`, `OS_BUFFERED`, and `none` are labelled separately. `OS_BUFFERED` must not be described as durable acknowledgement latency. |
| Resource validity      | The run completes without a timeout, consumer failure, invalid final state, or failed cleanup.                                     |

The project does not currently set a universal latency or throughput threshold. A threshold would only be meaningful
after fixing the hardware, JVM, workload, durability mode, and event consumer. The acceptance rule is therefore valid
execution plus transparent measurement, rather than an arbitrary number of operations per second.

## Measurement protocol

Run the full benchmark suite with:

```sh
./mvnw verify -Pbenchmarks
```

Maven Failsafe starts independent JVMs. By default, the suite uses three forks and three warmups, a processor capacity
of 1,024, and a 512 MB heap with G1 GC. It writes CSV results and environment metadata to `target/benchmark-results`.

For a short smoke run:

```sh
./mvnw verify -Pbenchmarks \
  -Dbenchmark.count=8000 \
  -Dbenchmark.saturationCount=8000 \
  -Dbenchmark.forks=1 \
  -Dbenchmark.warmups=1
```

The important configurable properties are:

- `benchmark.count` — commands in a fixed-rate run;
- `benchmark.saturationCount` — commands in a saturation run;
- `benchmark.forks` — fresh JVM forks;
- `benchmark.warmups` — warmups per fork;
- `benchmark.timeoutSeconds` — child-process timeout;
- `benchmark.output` — output directory.

Use a new output directory for each experiment. Counts must be divisible by eight because the workload is cycle-based.

Latency starts at the scheduled arrival for fixed-rate runs, or immediately before publication for saturation runs. It
ends after processing and event-digest callbacks. It includes submission allocation, backpressure, queueing, matching,
journaling, and callback work. It excludes startup, final verification, and file close. Exact quantiles are calculated
after the timed section.

In `SYNC` mode, each command includes the journal force. In `OS_BUFFERED` mode, the write is not forced for every
command, so the result measures a different durability boundary. `none` bypasses the journal and is useful for
separating matching and pipeline costs from persistence costs.

## Current report

On 16 September 2026, the standard Maven test suite passed 35 tests with zero failures. This confirms matching, journal,
recovery, and processor correctness, but it is not a performance measurement.

A short benchmark smoke run was also completed with one fork and one warmup:

```sh
./mvnw -B verify -Pbenchmarks \
  -Dbenchmark.count=8000 \
  -Dbenchmark.saturationCount=8000 \
  -Dbenchmark.forks=1 \
  -Dbenchmark.warmups=1
```

The run used Java 26.0.2.1 on a 10-core Apple Silicon machine, a 512 MB heap, G1 GC, capacity 1,024, and `none`
durability. Every configuration produced 12,000 events and the same event digest (`4289704653684797653`). The results
were:

| Processor           | Load                 | Throughput (commands/s) |       p50 |       p99 |     p99.9 |
|---------------------|----------------------|------------------------:|----------:|----------:|----------:|
| Queue, blocking     | Fixed rate 100,000/s |              100,003.91 |   5.50 µs |  39.75 µs | 110.79 µs |
| Disruptor, blocking | Fixed rate 100,000/s |               99,986.56 |   5.13 µs |  12.42 µs |  49.79 µs |
| Disruptor, yielding | Fixed rate 100,000/s |              100,005.57 |   0.33 µs |  20.42 µs |  81.25 µs |
| Queue, blocking     | Saturation           |            3,608,750.14 | 191.38 µs | 221.83 µs | 223.00 µs |
| Disruptor, blocking | Saturation           |            2,774,406.10 |  86.29 µs | 363.54 µs | 368.54 µs |
| Disruptor, yielding | Saturation           |            2,195,063.19 | 250.50 µs | 453.42 µs | 472.21 µs |

These figures are a smoke-test report, not a stable performance claim. They use one fork and one warmup on one machine.
Before publishing a benchmark comparison, use the full multi-fork protocol, retain the CSV files and environment
metadata, and report the variation between runs.

## Interpretation and limitations

The benchmark measures this implementation and workload. It does not establish behavior for deeper books, burst traffic,
cancel-heavy traffic, sweep-heavy traffic, multiple instruments, realistic event consumers, network transport, or
physical power loss.

The order book uses standard maps and allocates resting orders, price levels, commands, and pipeline submissions. The
benchmark therefore makes no zero-allocation claim. Sub-microsecond results may approach the cost of the timestamp reads
and event digest used for measurement.

For individual experiments, compile the benchmark sources first and then run the benchmark entry point:

```sh
./mvnw verify -Pbenchmarks -DskipITs

java -Xms512m -Xmx512m -XX:+UseG1GC \
  -cp "target/test-classes:target/exchange.jar" \
  com.exchange.benchmark.BenchmarkMain \
  DISRUPTOR 200000 100000 1024 yielding none 3
```

Record CPU placement, power settings, JVM version, heap options, processor mode, durability mode, and output revision
with the result. Compare the spread of each run's percentiles instead of reporting only one favourable run.
