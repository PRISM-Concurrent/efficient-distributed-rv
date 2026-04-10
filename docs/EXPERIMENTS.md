# Experiments

This document explains how to reproduce the results reported in the paper
and provides additional examples demonstrating the framework's capabilities.

---

## Reproducing the Paper's Results

### Setup

All experiments use `java.util.concurrent.ConcurrentLinkedQueue`
with operations `offer` and `poll`. The benchmark methodology follows
Georges et al. (OOPSLA 2007): 5 warmup rounds discarded, 10 measured
rounds per cell, reporting mean execution time.

**Table C** (instrumentation overhead) was run on a 64-core Linux server
(OpenJDK 21.0.10, `-Xmx16g`).
**Table D** (verdict accuracy) was run on a 10-core macOS laptop
(JVM 21.0.2, `-Xmx4g`).

### Commands

```bash
mvn clean package -DskipTests

# Table C — instrumentation overhead (no verification)
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark \
     --only=tableC --format=org --output=results/tableC.org

# Table D — verdict accuracy (full pipeline)
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark \
     --only=tableD --format=org --output=results/tableD.org
```

For the AspectJ strategy, prepend:

```bash
java -javaagent:$HOME/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
     --add-opens java.base/java.lang=ALL-UNNAMED ...
```

For large workloads (50k–100k ops) on a machine with sufficient memory:

```bash
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark \
     --only=tableC --format=org --output=results/tableC-large.org
```

---

### Table C — Instrumentation Overhead

`taskProducers()` only — no linearizability checking.
Format: mean ms. Threads = 4 (fixed).

| Ops     | CollectFAInc | CollectRAW | AspectJ |
|---------|-------------|------------|---------|
| 100     | 0           | 2          | 2       |
| 500     | 9           | 9          | 7       |
| 1 000   | 20          | 21         | 16      |
| 5 000   | 101         | 101        | 79      |
| 10 000  | 204         | 204        | 160     |
| 50 000  | 1 031       | 1 028      | 869     |
| 100 000 | 2 005       | 2 002      | 1 757   |

Overhead vs. thread count (ops = 10 000, fixed):

| Threads | CollectFAInc | CollectRAW | AspectJ |
|---------|-------------|------------|---------|
| 2       | 160         | 159        | 133     |
| 4       | 204         | 204        | 160     |
| 8       | 373         | 379        | 250     |
| 16      | 476         | 482        | 378     |
| 32      | 560         | 563        | 439     |
| 64      | 608         | 593        | 507     |

**Interpretation.**
`CollectFAInc` and `CollectRAW` show virtually identical overhead
(difference < 1% in all cells). Both scale linearly with operation count:
10× more operations yield approximately 10× more time. Scaling with thread
count is sub-linear: going from 2 to 64 threads (32×) increases overhead
by only ~4× at 10 000 operations, indicating effective work distribution
across cores.

AspectJ is consistently 20–30% faster in raw instrumentation time because
`@AfterReturning` records a single event per operation after completion,
whereas the snapshot implementations record two events (invocation and
response) with shared-state access in between. This performance advantage,
however, comes at the cost of verdict reliability, as shown in Table D.

---

### Table D — Verdict Accuracy

Full pipeline (instrumentation + verification). 100 runs per cell.
Ops = 100. `ConcurrentLinkedQueue` is a correct implementation —
expected verdict is always linearizable (%T = 100, %F = 0, %E = 0).

| Threads | GAI %T | GAI %F | GAI %E | RAW %T | RAW %F | RAW %E | AJ %T | AJ %F | AJ %E |
|---------|--------|--------|--------|--------|--------|--------|-------|-------|-------|
| 2       | 100    | 0      | 0      | 100    | 0      | 0      | 72    | 28    | 0     |
| 4       | 100    | 0      | 0      | 100    | 0      | 0      | 89    | 11    | 0     |

**Interpretation.**
`CollectFAInc` and `CollectRAW` produce correct verdicts in 100% of runs
across both thread configurations. AspectJ produced false negatives in
28% of runs with 2 threads and 11% with 4 threads. This is consistent
with the non-atomicity of `@AfterReturning` instrumentation identified
by El-Hokayem and Falcone (RV 2018): when a context switch occurs between
an operation's completion and its trace capture, the observed execution
differs from the actual one, producing an incorrect non-linearizable verdict
for an implementation that is in fact linearizable.

Across multiple independent experiments (10–100 runs per cell, ops = 100),
AspectJ's false negative rate ranged from 3–60% depending on thread count
and run conditions, confirming that the effect is real but variable. The
snapshot-based strategies showed 0% false negatives in all experiments.

**Note on sample size.**
Due to the NP-completeness of linearizability checking, verdict experiments
are limited to 100 operations and 100 runs per cell. The observed trend is
consistent with the theoretical predictions of El-Hokayem and Falcone (2018).

---

## Additional Examples

These examples are not reported in the paper but demonstrate the
framework's broader capabilities.

### Batch Execution — 8 Java Concurrent Algorithms

Verifies all supported algorithms and prints a summary verdict.

```bash
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.BatchExecution
```

All 8 standard Java concurrent collections are verified as linearizable:
`ConcurrentLinkedQueue`, `ConcurrentHashMap`, `ConcurrentLinkedDeque`,
`LinkedBlockingQueue`, `ConcurrentSkipListSet`, `LinkedTransferQueue`,
`ConcurrentSkipListMap`, `LinkedBlockingDeque`.

This is useful as a quick sanity check after building the project.

### Non-Linearizable Detection

Validates that the framework correctly detects violations using two
intentionally broken implementations included in the repository.

```bash
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     NonLinearizableTest
```

`BrokenQueue` and `NonLinearizableQueue` are both correctly identified
as non-linearizable. This confirms that the monitoring layer functions
as expected before running experiments on real implementations.

### Workload Pattern Comparison

Demonstrates how different operation generation strategies
(random, producer-consumer, read-heavy, write-heavy) affect
instrumentation and verification cost for the same implementation.

```bash
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.ProducersBenchmark --format=org
```

Read-heavy workloads tend to produce faster verification because
read-only operations introduce fewer ordering constraints into the
history passed to the JIT-Lin checker.