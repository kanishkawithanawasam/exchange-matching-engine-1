# Test evaluation

## 1. Evaluation date and command

The standard test suite was run on 16 September 2026 with:

```sh
./mvnw test
```

## 2. Result

```text
Tests run: 35
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

The test run completed in approximately two seconds. It required no external services.

## 3. Tests executed

| Test class               |  Tests | Result     |
|--------------------------|-------:|------------|
| `PriceLevelTest`         |      3 | Passed     |
| `OrderBookTest`          |      6 | Passed     |
| `ExchangeFactoryTest`    |      2 | Passed     |
| `ArchitectureTest`       |      1 | Passed     |
| `ExchangeServiceTest`    |      4 | Passed     |
| `JournalRecordCodecTest` |      1 | Passed     |
| `FileCommandJournalTest` |     11 | Passed     |
| `CommandProcessorTest`   |      7 | Passed     |
| **Total**                | **35** | **Passed** |

## 4. Evaluation against the specification

The run provides evidence for the following areas:

- order acceptance, matching, FIFO ordering, partial fills, and cancellation;
- market-order expiry and invalid-command handling;
- exchange creation and resource ownership;
- application-level journal-before-match behavior;
- journal encoding, replay, corruption checks, incomplete-frame recovery, and file locking;
- dependency boundaries enforced by ArchUnit;
- command-processor ordering, capacity, visibility, timeout, drain, and failure behavior.

The repository also contains deterministic randomized tests that compare the production order book with an independent
matcher, along with compatibility and crash-recovery scenarios. The standard run above is the current unit and component
result; benchmark-profile integration tests are reported separately when that profile is executed.

The short benchmark profile was then run with one fork and one warmup. It passed 39 unit/component tests and one
benchmark integration test. All six benchmark configurations completed successfully, produced 12,000 events, and
produced the same event digest. The run used Java 26.0.2.1 on a 10-core Apple Silicon machine. Detailed throughput and
latency values are recorded in [PERFORMANCE.md](PERFORMANCE.md).

## 5. Interpretation

The zero-failure run supports the conclusion that the tested matching, journal, recovery, architecture, and processor
behaviors conform to the current specification for the covered cases.

It does not prove that every possible command sequence is correct. It also does not prove durability against physical
power loss, filesystem write reordering, a full disk, or arbitrary hardware failure. Network behavior, authentication,
risk controls, multiple instruments, and production outbound-feed delivery are outside the current test scope.

## 6. Additional evaluation

Run the packaged verification build with:

```sh
./mvnw verify
```

Run the separate benchmark integration suite with:

```sh
./mvnw verify -Pbenchmarks
```

The benchmark suite launches fresh JVMs and validates final state, event count, and event digest before accepting a
result. Its latency and throughput measurements belong in the performance report and should not be mixed with the
correctness result above.

## 7. Future work

Useful additions would include longer randomized runs, more varied workloads, explicit filesystem and disk-full tests,
Java memory-ordering stress tests if the concurrency design changes, and recovery testing against controlled process and
machine failures.
