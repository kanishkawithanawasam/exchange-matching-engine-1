# Testing documentation

The testing documentation is split into two parts:

- [Test specification](TEST_SPECIFICATION.md) — expected behavior, invariants, test levels, and acceptance criteria.
- [Test evaluation](TEST_EVALUATION.md) — commands run, current results, interpretation, and remaining limitations.

Run the standard suite with:

```sh
./mvnw test
```

Run packaging and verification with:

```sh
./mvnw verify
```

The benchmark profile is separate from the normal correctness suite:

```sh
./mvnw verify -Pbenchmarks
```
