# Quick Start Guide

## Installation

```bash
cd code/efficient-distributed-rv
mvn clean install
```

## Run examples

### Deterministic schedule (best for debugging/repro)

```bash
# Simple example
./run-test.sh
```
### Comprehensive Demo


```bash
# Complete demo
mvn clean compile
./run-example.sh ComprehensiveDemo
```

The table below summarizes the execution of all major features of the framework
within a single run.

Each row corresponds to a **different usage scenario**, executed sequentially
using the same verification pipeline.

Columns:

- **Producers (ms)**
  Time spent executing operations and recording events
  (instrumentation + snapshot collection).

- **Verifier (ms)**
  Time spent by the JIT linearizability checker.

- **Total (ms)**
  End-to-end execution time (Producers + Verifier).

- **Result**
  Linearizability outcome reported by the checker.

```text
=== Comprehensive Demo Summary ===
┌──────────────────────┬────────────┬────────────┬────────────┬────────────┐
│ Scenario             │ Producers  │ Verifier   │ Total      │ Result     │
├──────────────────────┼────────────┼────────────┼────────────┼────────────┤
│ RichResult+Timeout   │       8 ms │     123 ms │     132 ms │ LIN        │
│ ConfigThreadsOps     │       5 ms │    1300 ms │    1305 ms │ LIN        │
│ AlgLib+Workload      │       3 ms │   33436 ms │   33440 ms │ LIN        │
│ EndToEndSmall        │       1 ms │       0 ms │       1 ms │ LIN        │
└──────────────────────┴────────────┴────────────┴────────────┴────────────┘
```

What do these scenarios show?

- RichResult+Timeout
Demonstrates the basic VerificationFramework usage,
timeout handling, and rich result reporting.
- ConfigThreadsOps
Shows how increasing threads and operations increases
the verification cost, while producer overhead remains small.
- AlgLib+Workload
Exercises algorithm discovery and workload-based execution.
The large verifier time reflects a more complex state space
induced by structured workloads.
- EndToEndSmall
A minimal end-to-end check, useful for sanity testing
and quick integration checks.

### Compare random vs workloads (PC/read/write heavy)


```bash
# ConcurrentLinkedQueue verificatio with three different generation patterns
./run-example.sh SingleImplementationPatternsDemo
```

Interpretation of pattern-based results

This experiment evaluates how different operation generation strategies
affect both execution cost and verification cost for a single implementation
(ConcurrentLinkedQueue).

Each row corresponds to the same object, the same number of threads,
and the same number of operations, but with a different workload pattern.

Columns:

- Producers: Time spent executing operations and recording events (instrumentation + snapshot).
- Verifier: Time spent by the JIT linearizability checker.
- Total: End-to-end verification time.
- Throughput: Computed as operations / total_time.

```bash
=== Patterns Comparison Summary ===
Threads    : 8
Operations : 20

┌──────────────────────────────┬────────────┬────────────┬────────────┬────────────┐
│ Pattern                      │ Producers  │ Verifier   │ Total      │ Throughput │
├──────────────────────────────┼────────────┼────────────┼────────────┼────────────┤
│ Random                       │      10 ms │      62 ms │      73 ms │        274 │
│ Producer-Consumer (70% prod) │       6 ms │      76 ms │      82 ms │        244 │
│ Read-heavy (80% reads)       │       2 ms │       1 ms │       3 ms │       6667 │
│ Write-heavy (80% writes)     │       2 ms │       2 ms │       4 ms │       5000 │
└──────────────────────────────┴────────────┴────────────┴────────────┴────────────┘

Summary:
  Linearizable: 4 / 4
  Throughput computed as: operations / total_time
```

What do we learn from these results?

- Random and Producer–Consumer workloads
Tend to produce more complex interleavings, which increases
verification time.
- Read-heavy workloads
Often lead to very fast verification because read-only operations
introduce fewer ordering constraints.
- Write-heavy workloads
Still execute quickly, but introduce more conflicts than read-heavy
patterns, leading to moderate verification cost.

Importantly, all workloads were verified as linearizable, confirming
that the framework is robust across different execution shapes.

### Verified Java Concurrent Algorithms (50 operations)

```bash
# Batch example: All Java concurrent algorithms
mvn clean compile
./run-example.sh BatchExecution
```

All verified as **LINEARIZABLE** with JitLin checker:

```bash
=== Batch Execution Summary ===
Threads    : 4
Operations : 100

┌──────────────────────────┬────────────┬────────────┬────────────┬────────────┐
│ Algorithm                │ Producers  │ Verifier   │ Total      │ Throughput │
├──────────────────────────┼────────────┼────────────┼────────────┼────────────┤
│ ConcurrentLinkedQueue    │       12 ms │      168 ms │      181 ms │      552 │
│ ConcurrentHashMap        │        6 ms │       50 ms │       56 ms │     1786 │
│ ConcurrentLinkedDeque    │        3 ms │       52 ms │       56 ms │     1786 │
│ LinkedBlockingQueue      │        4 ms │       44 ms │       48 ms │     2083 │
│ ConcurrentSkipListSet    │        6 ms │       60 ms │       67 ms │     1493 │
│ LinkedTransferQueue      │        6 ms │       42 ms │       49 ms │     2041 │
│ ConcurrentSkipListMap    │        3 ms │       38 ms │       42 ms │     2381 │
│ LinkedBlockingDeque      │        3 ms │       38 ms │       41 ms │     2439 │
└──────────────────────────┴────────────┴────────────┴────────────┴────────────┘

Summary:
  Linearizable: 8 / 8
  Total time (linearizable only): 540 ms
```

Interpretation:

- **Producers (ms):** time to execute operations and record the execution (instrumentation only).
- **Verifier (ms):** time spent by the JIT linearizability checker.
- **Total (ms):** Producers + Verifier.
- **Throughput (ops/sec):** computed as `Operations / TotalTimeSeconds` (includes verification).

Note:

- In these runs, verification dominates the cost; producer time is typically small compared to the checker.
- Different algorithms lead to different state spaces for the checker, so verification time varies significantly.


### Timeout

Demonstrates withTimeout(...) safety

```bash
# Batch example: All Java concurrent algorithms
mvn clean compile
./run-example.sh TimeoutDemo
```

### Validation (Non-linearizable Test)

```bash
#
mvn clean compile
./run-example.sh NonLinearizableTest
```

This experiment validates that the framework correctly detects violations
of linearizability.

Two intentionally broken queue implementations are tested:

- **BrokenQueue**: returns incorrect results intermittently
- **NonLinearizableQueue**: breaks FIFO order by design

Results:


```bash
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

### Snapshot Throughput Comparison (Producers Only)

As part of the quick-start evaluation, we measure the throughput of the instrumentation layer only, i.e., the time required to produce and record concurrent operations, without running the verifier.

```bash
# Snapshot Comparison
mvn clean compile
./run-example.sh SnapshotThroughputComparisonDemo
```

- Workload: 5,000 operations
- Threads: 4
- Runs per snapshot: 5
- Verification: disabled
- Workload: identical deterministic schedule for all runs

This experiment isolates the cost of execution + snapshot instrumentation.

Results (averaged over 5 runs)

```bash
=== Final Comparison (averaged over 5 runs) ===
┌───────────┬──────────────┬──────────────┬──────────────┐
│ Snapshot  │ Avg time ms  │ Std dev ms   │ Throughput   │
├───────────┼──────────────┼──────────────┼──────────────┤
│ gAIsnap   │ 232.40       │ 14.94        │ 21606        │
│ rawsnap   │ 212.02       │ 12.95        │ 23679        │
└───────────┴──────────────┴──────────────┴──────────────┘

Relative speed:
  GAI / RAW throughput = 0.91×
  → RAW is faster on average
  ```

Interpretation of the Results

- Avg time (ms)
Average wall-clock time required to execute and record all operations
using taskProducersSeed(...).
- Std dev (ms)
Standard deviation across 5 runs, indicating low variance and stable
measurements.
- Throughput (ops/sec)
Number of operations recorded per second by the instrumentation layer.
Higher is better.

Which Snapshot Is Faster?

- RAW snapshot (rawsnap) is faster on average in this experiment.
- It achieves approximately 9% higher throughput than gAIsnap
for the same workload and number of threads.

Why Can RAW Be Faster?

Although rawsnap captures more precise causality information
(happens-before relations via read-after-write),
its implementation relies on immutable persistent data structures
and avoids a global fetch-and-increment contention point.

In contrast:

- GAI snapshot (gAIsnap)
- Uses a global atomic fetch-and-increment
- Imposes a total order on events
- Can introduce contention under high concurrency


