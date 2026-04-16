# A Reliable Non-Intrusive Runtime Verification Framework for Linearizability

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-78%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-21-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()
[![GitHub](https://img.shields.io/badge/GitHub-public-green)](https://github.com/PRISM-Concurrent/efficient-distributed-rv)

---

## 1. Research Context

Linearizability is the standard correctness condition for concurrent data structures:
informally, every operation must appear to take effect instantaneously at some point
between its invocation and its response. Even when an algorithm has been proved correct
theoretically, translating it into code may introduce subtle races that violate
linearizability — errors that are difficult to detect with conventional testing.

Runtime Verification (RV) addresses this by checking correctness properties of the
current execution of a system. Verifying linearizability at runtime involves two tasks:
**detecting** the current execution, and **deciding** whether it is linearizable.
Deciding is NP-complete, so practical tools construct a linearization witness when one
exists. Detecting is equally challenging: existing RV tools rely on intrusive
instrumentation techniques such as bytecode weaving (e.g., AspectJ), which can alter
the observed execution and introduce false positives and false negatives — the observed
execution may differ from the one that would occur without instrumentation
([El-Hokayem & Falcone, RV 2018](https://doi.org/10.1007/978-3-030-03769-7_6)).

This framework addresses both tasks without modifying the system under inspection.
For detection, it uses two distributed algorithms based on **non-linearizable snapshot
objects** (collect objects): `CollectFAInc`, which induces a total order via atomic
fetch-and-increment, and `CollectRAW`, which reconstructs the happens-before relation
from cross-thread snapshot views. Both obtain the current execution asynchronously and
guarantee that if the collected execution is not linearizable, the real execution is
also not linearizable — providing a sound verdict and avoiding the false negatives
introduced by intrusive instrumentation. For the decision task, the monitoring layer
invokes a JIT-based linearizability checker, producing a witness when the execution
is linearizable.

> **Paper:** Rodríguez, G. V. & Castañeda, A. (2024).
> *Towards Efficient Runtime Verified Linearizable Algorithms.*
> In: Runtime Verification (RV 2024). LNCS 15278, pp. 262–281. Springer.
> [https://doi.org/10.1007/978-3-031-74234-7_17](https://doi.org/10.1007/978-3-031-74234-7_17)

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

Both collect strategies are *non-linearizable* by design. This is intentional and
sufficient for the following reason: if the observed execution is not linearizable,
neither is the real one — providing a **sound** verdict without modifying the system
under inspection. When the observed execution *is* linearizable, the JIT-Lin checker
confirms it and produces a **linearization witness**. This is the best achievable
guarantee given the impossibility of fully verifying linearizability at runtime.

---

## 3. Quick Start

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
    .withSnapshot("gAIsnap") ...

// CollectRAW — non-linearizable collect
VerificationFramework.verify(ConcurrentLinkedQueue.class)
    .withSnapshot("rawsnap") ...
```

---

## 4. Running the Experiments

See **[docs/EXPERIMENTS.md](docs/EXPERIMENTS.md)** for full instructions, result tables, and interpretation.

```bash
# Table C — instrumentation overhead
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark --only=tableC --format=org --output=results/tableC.org

# Table D — verdict accuracy (correct implementation)
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark --only=tableD --format=org --output=results/tableD.org

# Table D — BrokenQueue (severely broken)
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark --only=tableD \
     --broken=phd.distributed.verifier.BrokenQueue --format=org --output=results/tableD-broken.org

# Table D — PartialSyncQueue (subtly broken, use ops=60)
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark --only=tableD \
     --broken=phd.distributed.verifier.PartialSyncQueue --format=org --output=results/tableD-partialsync.org

# Table E — coverage across all data structure types
java -Xmx16g -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark --only=tableE --format=org --output=results/tableE.org
```

For the AspectJ strategy, prepend `-javaagent` and `--add-opens`:

```bash
java -javaagent:$HOME/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
     --add-opens java.base/java.lang=ALL-UNNAMED -Xmx16g \
     -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.VerifierBenchmark ...
```


---

## 5. Architecture (Top-Down)

### 5.1 Overview

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

### 5.2 How It Works

Each verification run goes through three phases:

1. **Instrumentation** (`taskProducers`)
   Concurrent threads call operations on the target data structure.
   `Wrapper` records an invocation event before each call (`Snapshot.write`)
   and a response event after it (`Snapshot.snapshot`).
   No locks are acquired on the object under inspection.

2. **History construction** (`Snapshot.buildXE`)
   After all threads finish, the snapshot builds the execution history X_E —
   a vector of `{:invoke, :return}` events ordered by the collect's partial order.
   `CollectFAInc` uses a global atomic counter to impose a total order.
   `CollectRAW` uses view containment between collected snapshots to reconstruct
   the happens-before relation, then linearizes it via topological sort.

3. **Linearizability checking** (`taskVerifiers` → `JitLinChecker`)
   X_E is passed to Lowe's JIT-Lin algorithm, which searches for a valid
   sequential interleaving using DFS with early pruning.

---

## 6. Supported Data Structures

The framework supports any Java class instantiable with a no-arg constructor.
The following have been exercised in experiments:

| Type | Implementations |
|------|----------------|
| Queue | `ConcurrentLinkedQueue`, `LinkedBlockingQueue`, `LinkedTransferQueue` |
| Deque | `ConcurrentLinkedDeque`, `LinkedBlockingDeque` |
| Set | `ConcurrentSkipListSet` |
| Map | `ConcurrentHashMap`, `ConcurrentSkipListMap` |

Sequential specifications are provided in `src/main/clojure/spec/`.

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
- **Armando Castañeda** — `armando.castanedar@gmail.com`
- **Issues:** [GitHub Issues](https://github.com/PRISM-Concurrent/efficient-distributed-rv/issues)

---

## Documentation

| Document | Audience | Purpose |
|----------|----------|---------|
| [QUICK_START.md](QUICK_START.md) | Everyone | Run your first verification in 5 minutes |
| [INSTALL.md](INSTALL.md) | Everyone | Full installation instructions |
| [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) | Reviewers | Reproduce the paper's tables |
| [docs/API_USAGE_GUIDE.org](docs/API_USAGE_GUIDE.org) | Developers | Complete API reference |
| [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) | Developers | Runnable examples by use case |
| [docs/USER_MANUAL.md](docs/USER_MANUAL.md) | Developers | Configuration, troubleshooting, extending |