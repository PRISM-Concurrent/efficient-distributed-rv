# API Examples

This document provides **runnable examples** of the public API, organized by use case.
Each example corresponds to a file in `src/main/java/` that can be run directly.

For API semantics and configuration options, see [`docs/API_USAGE_GUIDE.org`](API_USAGE_GUIDE.org).
For reproducing the paper's benchmark results, see [`docs/EXPERIMENTS.md`](EXPERIMENTS.md).

---

## Running the Examples

All examples are compiled as part of the main build:

```bash
mvn clean package -DskipTests
```

Then run with:

```bash
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar <ClassName>
```

---

## 1. Minimal Verification

**File:** `src/main/java/MinimalExample.java`

The shortest possible verification — one implementation, default settings.

```java
VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withOperations(40)
    .run();

System.out.println("Linearizable: " + result.isLinearizable());
```

```bash
java -cp target/...jar MinimalExample
# Output: Linearizable: true
```

Use this to confirm the build is working before running longer experiments.

---

## 2. Batch Verification — 8 Correct Implementations

**File:** `src/main/java/BatchExecution.java`

Verifies all 8 supported Java concurrent collections (threads = 4,
ops = 100) and prints a summary table with producer time, verifier
time, and throughput per implementation.

```bash
java -cp target/...jar BatchExecution
```

Expected output (abbreviated):

```
=== Batch Execution Summary ===
Threads    : 4
Operations : 100

┌──────────────────────────┬────────────┬────────────┬────────────┬────────────┐
│ Algorithm                │ Producers  │ Verifier   │ Total      │ Throughput │
├──────────────────────────┼────────────┼────────────┼────────────┼────────────┤
│ ConcurrentLinkedQueue    │       12 ms │      168 ms │      181 ms │        552 │
│ ConcurrentHashMap        │        6 ms │       50 ms │       56 ms │       1786 │
...
│ LinkedBlockingDeque      │        3 ms │       38 ms │       41 ms │       2439 │
└──────────────────────────┴────────────┴────────────┴────────────┴────────────┘

Summary:
  Linearizable: 8 / 8
```

Use this as a sanity check that the full pipeline works across all data
structure types before running experiments.

---

## 3. Choosing a Snapshot Strategy

**Files:**
- `src/main/java/BatchGAISnapshotTest.java` — uses `CollectFAInc` (`gAIsnap`)
- `src/main/java/HighPerformanceHW.java` — uses `CollectRAW` (`rawsnap`)

Both files verify the same implementations but with different snapshot
strategies. The `gAIsnap` strategy uses an atomic fetch-and-increment
counter to impose a total order; `rawsnap` reconstructs happens-before
via view containment.

```java
// CollectFAInc
VerificationFramework.verify(ConcurrentLinkedQueue.class)
    .withThreads(4).withOperations(100)
    .withObjectType("queue").withMethods("offer", "poll")
    .withSnapshot("gAIsnap")
    .run();

// CollectRAW
VerificationFramework.verify(ConcurrentLinkedQueue.class)
    .withThreads(4).withOperations(100)
    .withObjectType("queue").withMethods("offer", "poll")
    .withSnapshot("rawsnap")
    .run();
```

```bash
java -cp target/...jar BatchGAISnapshotTest
java -cp target/...jar HighPerformanceHW
```

Both strategies produce sound verdicts. `CollectFAInc` has slightly lower
overhead; `CollectRAW` captures more precise causal information.

---

## 4. Workload-Based Verification

**Files:**
- `src/main/java/BatchWorkloadTest.java`
- `src/main/java/HighPerformanceLinearizabilityWorkloadTest.java`

These examples show how to use `WorkloadPattern` to control the
operation mix. Thread assignment is handled automatically by the
`Executioner`.

```java
// Producer-consumer: 70% writes, 30% reads
WorkloadPattern pc = WorkloadPattern.producerConsumer(100, 4, 0.7);

// Read-heavy: 80% reads, 20% writes
WorkloadPattern rh = WorkloadPattern.readHeavy(100, 4, 0.8);

// Write-heavy: 80% writes, 20% reads
WorkloadPattern wh = WorkloadPattern.writeHeavy(100, 4, 0.8);

VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4).withOperations(100)
    .withObjectType("queue").withMethods("offer", "poll")
    .withWorkload(pc)
    .run();
```

```bash
java -cp target/...jar BatchWorkloadTest
java -cp target/...jar HighPerformanceLinearizabilityWorkloadTest
```

Read-heavy workloads tend to produce faster verification because read-only
operations introduce fewer ordering constraints into the history passed to
JIT-Lin. Write-heavy and producer-consumer workloads expose more
interleavings and are therefore better for detecting violations.

---

## 5. Stress Testing and Detection Improvement

**File:** `src/main/java/ImprovedDetectionExample.java`

Demonstrates three ways to increase the probability of detecting a
linearizability violation: larger operation counts, more threads, and
targeted workload patterns.

```bash
java -cp target/...jar ImprovedDetectionExample
```

The demo shows three scenarios:

- **Low stress:** 1 000 operations, 4 threads
- **Medium stress:** 5 000 operations, 8 threads
- **High stress:** 10 000 operations, 8 threads

More operations and threads increase the chance that a race condition
manifests in a given run. For subtle violations (e.g., `PartialSyncQueue`),
this is the most effective way to improve detection rate.

---

## 6. Detecting a Non-Linearizable Implementation

**File:** `src/main/java/NonLinearizableTest.java`

Validates that the framework correctly identifies violations in two
intentionally broken implementations:

- `BrokenQueue` — severe race condition, detected in 100% of runs
- `NonLinearizableQueue` — breaks FIFO order by design

```bash
java -cp target/...jar NonLinearizableTest
```

Expected output:

```
=== Non-Linearizable Test Summary ===
Threads    : 4
Operations : 100

┌──────────────────────────┬────────────┬────────────┬────────────┬────────────┐
│ Algorithm                │ Producers  │ Verifier   │ Total      │ Result     │
├──────────────────────────┼────────────┼────────────┼────────────┼────────────┤
│ BrokenQueue              │      12 ms │     706 ms │     718 ms │ NOT LIN    │
│ NonLinearizableQueue     │       6 ms │   13310 ms │   13316 ms │ NOT LIN    │
└──────────────────────────┴────────────┴────────────┴────────────┴────────────┘

Summary:
  Correctly detected as NOT linearizable: 2 / 2
```

---

## 7. Comprehensive API Tour

**File:** `src/main/java/ComprehensiveDemo.java`

A single demo that exercises all major API features in sequence:

| Scenario | What it demonstrates |
|---|---|
| `RichResult+Timeout` | `verify(String)`, timeout, full `VerificationResult` |
| `ConfigThreadsOps` | Effect of increasing threads and operations on cost |
| `AlgLib+Workload` | `AlgorithmLibrary.search()` + `WorkloadPattern` |
| `EndToEndSmall` | Minimal end-to-end check for integration testing |

```bash
java -cp target/...jar ComprehensiveDemo
```

---

## 8. Low-Level API: Executioner + DistAlgorithm

**File:** `src/main/java/HighPerformanceLinearizabilityTest.java`

Shows how to use the low-level `Executioner` directly, bypassing the
fluent builder. Useful when you need fine-grained control over the
execution and verification phases.

```java
DistAlgorithm alg = new A(
    "java.util.concurrent.ConcurrentLinkedQueue",
    "offer", "poll"
);

Executioner exec = new Executioner(4, 100, alg, "queue");

exec.taskProducers();   // instrumentation phase only
exec.taskVerifiers();   // linearizability check
```

```bash
java -cp target/...jar HighPerformanceLinearizabilityTest
```

The low-level API is useful for integrating the framework into an
existing test harness where you want to control when each phase runs.

---

## 9. Choosing Method Subsets

Not every method needs to be included in the verification. Restricting
to a meaningful subset improves workload coherence and avoids
operations not covered by the sequential specification.

```java
// Queue — enqueue/dequeue only
.withObjectType("queue").withMethods("offer", "poll")

// Deque — front operations only
.withObjectType("deque").withMethods("offerFirst", "pollFirst")

// Set — updates only (no reads)
.withObjectType("set").withMethods("add", "remove")

// Map — updates only
.withObjectType("map").withMethods("put", "remove")
```

Do not include methods not covered by the sequential specification
(e.g., `size()`, `isEmpty()`). The checker may produce incorrect
results or fail to terminate.

---

## 10. Detecting a Non-Linearizable Implementation — Programmatic

```java
VerificationResult result = VerificationFramework
    .verify("phd.distributed.verifier.BrokenQueue")
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .run();

System.out.println("Linearizable: " + result.isLinearizable()); // false
```

---

## 11. Deterministic Schedule (Debugging)

Use a fixed sequence of operations to reproduce a specific failure or
build a minimal counterexample.

```java
A alg = new A(
    ConcurrentLinkedQueue.class.getName(),
    "offer", "poll"
);

MethodInf offer = null, poll = null;
for (MethodInf m : alg.methods()) {
    if (m.getName().equals("offer")) offer = m;
    if (m.getName().equals("poll"))  poll  = m;
}

List<OperationCall> schedule = List.of(
    new OperationCall(1, offer),
    new OperationCall(null, poll),
    new OperationCall(2, offer)
);

VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(3)
    .withOperations(schedule.size())
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withSchedule(schedule)
    .run();
```

Deterministic schedules are fully reproducible across runs and are the
recommended approach for publishing minimal counterexamples alongside
bug reports.

---

## Summary of Example Files

| File | Use case | Key API |
|---|---|---|
| `MinimalExample.java` | Quickest verification | `verify(Class)` |
| `BatchExecution.java` | All 8 correct implementations | `AlgorithmLibrary`, table output |
| `BatchGAISnapshotTest.java` | `CollectFAInc` strategy | `withSnapshot("gAIsnap")` |
| `HighPerformanceHW.java` | `CollectRAW` strategy, 8 impls | `withSnapshot("rawsnap")` |
| `BatchWorkloadTest.java` | Workload patterns | `withWorkload(WorkloadPattern)` |
| `HighPerformanceLinearizabilityWorkloadTest.java` | Workload + multiple impls | `WorkloadPattern` variants |
| `ImprovedDetectionExample.java` | Stress testing | ops/threads scaling |
| `NonLinearizableTest.java` | Violation detection | `BrokenQueue`, `NonLinearizableQueue` |
| `ComprehensiveDemo.java` | Full API tour | All features |
| `HighPerformanceLinearizabilityTest.java` | Low-level control | `Executioner`, `DistAlgorithm` |