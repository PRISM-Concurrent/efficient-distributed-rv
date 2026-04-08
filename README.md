# Efficient Distributed Runtime Verification of Linearizability

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-78%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-21-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()
[![GitHub](https://img.shields.io/badge/GitHub-public-green)](https://github.com/PRISM-Concurrent/efficient-distributed-rv)

---

## 1. Research Context

<!-- TODO (author): Expand this paragraph with 2–3 sentences from the paper's introduction
     that describe the broader problem (concurrent data structures, correctness, the role
     of linearizability as the standard correctness condition). -->

Linearizability is the standard correctness condition for concurrent data structures.
Verifying it at runtime requires observing a concurrent execution and deciding whether
every operation appears to have taken effect atomically at some point between its
invocation and its response.

The classical approach builds this observation using a *snapshot* abstraction, but
snapshot implementations with optimal step complexity are notoriously complex.
This work proposes using the simpler *collect* abstraction instead.
The key theoretical result is that, under the Single-Writer Multiple-Reader (SWMR)
ownership discipline, a collect suffices to build a sound and complete runtime monitor
for linearizability — with a significantly simpler implementation.

> **Paper:** Rodríguez, G. V. & Castañeda, A. (2025).
> *Towards Efficient Runtime Verified Linearizable Algorithms.*
> In: Runtime Verification (RV 2024). LNCS 15278, pp. 262–281. Springer.

---

## 2. What This Repository Provides

This repository contains the **reference implementation** accompanying the paper.
It provides:

| Component | Description |
|-----------|-------------|
| `CollectFAInc` (GAIsnap) | Collect implementation using an atomic Fetch-And-Increment counter |
| `CollectRAW` (RAWsnap) | Collect implementation using a non-linearizable read-after-write collect |
| JIT-Lin checker | Gavin Lowe's JIT linearizability checker, bridged from Clojure |
| `VerificationFramework` | High-level fluent API for one-line integration |
| Experiment suite | Reproducible benchmarks comparing both strategies (see [EXPERIMENTS.md](EXPERIMENTS.md)) |

Both collect strategies are *non-linearizable* by design.
The paper proves that this is sufficient: if the observed execution is not
linearizable, neither is the real one.

---

## 3. Architecture (Top-Down)

### 3.1 Overview

```
User code / experiment
        │
        ▼
VerificationFramework       ← high-level fluent API (VerificationFramework.java)
        │
        ▼
   Executioner               ← orchestrates producers + verifier (Executioner.java)
     ├── Wrapper             ← per-operation write → apply → snapshot (Wrapper.java)
     │       ├── Snapshot    ← abstract base; CollectFAInc or CollectRAW
     │       └── A           ← reflective wrapper for any Java class (A.java)
     └── Verifier            ← calls JIT-Lin checker (Verifier.java)
             └── JitLinChecker ← bridges to Clojure typelin/jitlin namespaces
```

### 3.2 Verification Pipeline

Each verification run goes through three phases:

1. **Instrumentation phase** (`taskProducers`)
   Concurrent threads call operations on the target data structure.
   Before each call, `Wrapper` invokes `Snapshot.write()` to record the invocation.
   After each call, `Wrapper` invokes `Snapshot.snapshot()` to record the response.

2. **History construction** (`Snapshot.buildXE`)
   After all threads finish, the snapshot builds the *execution history* X_E —
   a vector of `{:invoke, :return}` events ordered by the collect's partial order.
   - `CollectFAInc` uses a global atomic counter to impose a total order.
   - `CollectRAW` uses the containment relation between collected views to impose a
     partial (topological) order.

3. **Linearizability checking** (`taskVerifiers` → `JitLinChecker`)
   The X_E history is passed to Lowe's JIT-Lin algorithm, which searches for a
   valid sequential interleaving using DFS with early pruning.

### 3.3 Snapshot Strategies

| | `CollectFAInc` (GAIsnap) | `CollectRAW` (RAWsnap) |
|---|---|---|
| Counter mechanism | `AtomicInteger.getAndIncrement()` | None |
| Order imposed | Total (by counter value) | Partial (by view containment) |
| Clojure namespace | `logtAs` | `logrAw` |
| SWMR guarantee | Each slot `i` written only by thread `i` | Same |
| Happens-before | `awaitTermination()` after all producers | Same |

Both strategies satisfy the SWMR discipline: the ArrayList holding per-thread
event logs is pre-allocated with one slot per process, and each thread writes
exclusively to its own slot. The verifier reads all slots only after
`awaitTermination()`, which establishes happens-before by the Java Memory Model.

### 3.4 Package Structure

```
src/
├── main/
│   ├── java/phd/distributed/
│   │   ├── api/           ← VerificationFramework, DistAlgorithm, A, AlgorithmLibrary
│   │   ├── core/          ← Executioner, Wrapper, Verifier, JitLinChecker
│   │   ├── snapshot/      ← Snapshot (abstract), CollectFAInc, CollectRAW
│   │   └── datamodel/     ← OperationCall, MethodInf, Event
│   └── clojure/
│       ├── logtAs.clj     ← per-thread event log for GAIsnap
│       ├── logrAw.clj     ← per-thread event log for RAWsnap
│       ├── jitlin.clj     ← JIT-Lin DFS checker
│       └── typelin.clj    ← sequential specifications (queue, deque, set, map)
└── experiments/
    └── java/phd/experiments/
        ├── BatchComparison.java       ← end-to-end strategy comparison
        ├── ProducersBenchmark.java    ← instrumentation-only overhead benchmark
        └── aspectj/
            ├── AspectJSnapshot.java              ← collect via bytecode interception
            └── LinearizabilityMonitorAspect.java ← AspectJ advice for A.apply()
```

---

## 4. Supported Data Structures

The framework supports any Java class that can be instantiated with a no-arg
constructor.  The following have been exercised in experiments:

**Queues:** `ConcurrentLinkedQueue`, `LinkedBlockingQueue`, `LinkedTransferQueue`
**Deques:** `ConcurrentLinkedDeque`, `LinkedBlockingDeque`
**Sets:** `ConcurrentSkipListSet`
**Maps:** `ConcurrentHashMap`, `ConcurrentSkipListMap`

Sequential specifications for JIT-Lin are provided for `queue`, `deque`, `set`,
and `map` in `src/main/clojure/spec/`.

---

## 5. Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+

### Build

```bash
git clone https://github.com/PRISM-Concurrent/efficient-distributed-rv
cd efficient-distributed-rv
mvn clean package -DskipTests
```

### Minimal Example

```java
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import java.util.concurrent.ConcurrentLinkedQueue;

VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .run();

System.out.println("Linearizable: " + result.isLinearizable());
System.out.println("Producer time: " + result.getProdExecutionTime().toMillis() + " ms");
System.out.println("Verifier time: " + result.getVerifierExecutionTime().toMillis() + " ms");
```

### Choosing a Snapshot Strategy

```java
// CollectFAInc — atomic counter (default)
VerificationFramework.verify(ConcurrentLinkedQueue.class)
    .withSnapshot("gAIsnap")
    ...

// CollectRAW — non-linearizable collect
VerificationFramework.verify(ConcurrentLinkedQueue.class)
    .withSnapshot("rawsnap")
    ...
```

---

## 6. Running the Experiments

See **[EXPERIMENTS.md](EXPERIMENTS.md)** for full instructions and result tables.

Quick commands:

```bash
# Strategy comparison (GAIsnap vs RAWsnap vs AspectJ)
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.BatchComparison

# Instrumentation-only overhead benchmark
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.ProducersBenchmark --format=org
```

For the AspectJ strategy, append:
```bash
-javaagent:$HOME/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
--add-opens java.base/java.lang=ALL-UNNAMED
```

---

## 7. Running the Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=AlgorithmLibraryTest

# Skip tests during package
mvn package -DskipTests
```

---

## 8. Extending the Framework

### Adding a New Sequential Specification

Create `src/main/clojure/spec/mytype.clj`:

```clojure
(ns spec.mytype)
(defn mytype-init [] <initial-state>)
(defn mytype-step [state op arg res] {:ok? <bool> :state <new-state>})
```

Register it in `typelin.clj` under `specs`.

### Adding a New Snapshot Strategy

1. Extend `phd.distributed.snapshot.Snapshot`.
2. Implement `write(int tid, Object inv)`, `snapshot(int tid, Object res)`,
   and `buildXE()`.
3. Pass the instance to `new Executioner(threads, ops, alg, type, mySnapshot)`.

---

## 9. Citation

```bibtex
@InProceedings{10.1007/978-3-031-74234-7_17,
  author    = {Rodr{\'i}guez, Gilde Valeria and Casta{\~{n}}eda, Armando},
  title     = {Towards Efficient Runtime Verified Linearizable Algorithms},
  booktitle = {Runtime Verification},
  year      = {2025},
  publisher = {Springer Nature Switzerland},
  pages     = {262--281},
  isbn      = {978-3-031-74234-7}
}
```

---

## 10. Contact

- **Gilde Valeria Rodríguez** — `gildevroji@gmail.com`
- **Miguel Piña** — `miguelpinia1@gmail.com`
- **Issues:** [GitHub Issues](https://github.com/PRISM-Concurrent/efficient-distributed-rv/issues)

---

## Acknowledgments

The RV 2024 community for insightful feedback.
Gavin Lowe for the JIT linearizability checker and accessible documentation.
