# User Manual

**Version:** 1.0.0
**License:** Apache 2.0

This manual covers configuration, troubleshooting, and known limitations.
For other topics, see the relevant document:

| Topic | Document |
|---|---|
| Installation and build | [INSTALL.md](../INSTALL.md) |
| First verification in 5 minutes | [QUICK_START.md](../QUICK_START.md) |
| Complete API reference | [docs/API_USAGE_GUIDE.org](API_USAGE_GUIDE.org) |
| Runnable code examples | [docs/API_EXAMPLES.md](API_EXAMPLES.md) |
| Reproducing paper results | [docs/EXPERIMENTS.md](EXPERIMENTS.md) |

---

## 1. Configuration

### 1.1 System Properties

Runtime behavior is controlled by `src/main/resources/system.properties`.
Edit before building and rebuild with `mvn clean package -DskipTests`.

#### Test modes

The test mode controls how many iterations the internal test harness
runs when exercising the verification pipeline:

```properties
# fast (default) — quick feedback during development
test.mode=fast

# thorough — suitable for CI/CD or pre-commit hooks
test.mode=thorough

# stress — exhaustive testing before a release
test.mode=stress
```

| Mode | Iterations | Approx. duration | Recommended for |
|---|---|---|---|
| `fast` | 100 | < 5 s | Development, quick checks |
| `thorough` | 1 000 | < 30 s | CI/CD, pre-commit |
| `stress` | 10 000 | < 5 min | Release validation |

#### Feature flags

```properties
# Async logging via Log4j2 async appender (production-ready, enabled by default)
feature.async.logging=true

# Parallel verification — experimental, not integrated in v1.0
feature.parallel.verification=false

# Smart state-space pruning — experimental, not integrated in v1.0
feature.smart.pruning=false

# Result caching between runs — experimental
feature.result.caching=false

# Object pooling — experimental
feature.object.pooling=false
```

All `feature.*` flags except `feature.async.logging` are disabled by
default and have no effect on the verification pipeline described in
the paper.

#### Checking flags in code

```java
if (SystemConfig.FEATURES.parallelVerification) {
    // parallel path (not active in v1.0)
} else {
    // standard sequential verification
}

// Or by name:
if (SystemConfig.FEATURES.isEnabled("smart.pruning")) {
    // pruning active
}
```

#### System and performance parameters

```properties
# Thread pool size for the internal executor service
system.thread.pool.size=8

# Default iteration count (used when test.mode is not set explicitly)
system.default.iterations=1000

# Default verification timeout in milliseconds
system.default.timeout.ms=30000

# Internal batch size for operation processing
system.batch.size=100

# Performance monitoring (disabled by default)
performance.monitoring.enabled=false
performance.profiling.enabled=false
```

**Note on performance parameters.** `performance.monitoring.enabled`
and `performance.profiling.enabled` are infrastructure placeholders.
They exist in `SystemConfig.java` but are not connected to the
verification pipeline in v1.0. Integrating them requires further
analysis of the interaction between profiling instrumentation and the
non-blocking guarantees of `CollectFAInc` and `CollectRAW` — adding
profiling hooks inside the instrumentation layer could reintroduce
the observer effect that the snapshot strategies are designed to avoid.
This is left as future work.

#### Runtime override via JVM arguments

Any property can be overridden at runtime without rebuilding:

```bash
# Override test mode
mvn test -Dtest.mode=stress

# Enable a feature flag
mvn test -Dfeature.parallel.verification=true

# Multiple overrides
mvn test -Dtest.mode=thorough -Dfeature.async.logging=false
```

Priority order (highest to lowest):
1. JVM system properties (`-D` flags)
2. `system.properties` file
3. Default values in `SystemConfig.java`

#### Complete quick reference

```properties
# System
system.thread.pool.size=8
system.default.iterations=1000
system.default.timeout.ms=30000
system.batch.size=100

# Logging
logging.async.enabled=true
logging.use.disruptor=true
logging.buffer.size=8192
logging.event.buffer.size=16384

# Testing
test.mode=fast

# Features
feature.async.logging=true
feature.parallel.verification=false
feature.smart.pruning=false
feature.result.caching=false
feature.object.pooling=false

# Performance
performance.monitoring.enabled=false
performance.profiling.enabled=false
```

### 1.2 Logging

Log output is controlled by `src/main/resources/log4j2.xml`.

**Reduce verbosity during experiments** (recommended for benchmarks):

```xml
<Root level="WARN">
    <AppenderRef ref="Console"/>
</Root>
```

**Enable detailed JIT-Lin output for debugging** (shows the execution
history passed to the checker):

```xml
<Root level="DEBUG">
    <AppenderRef ref="Console"/>
</Root>
```

**Async logging with Disruptor.** The framework supports high-throughput
async logging via the LMAX Disruptor ring buffer. This is enabled by
default (`feature.async.logging=true`). Buffer sizes can be tuned in
`system.properties`:

```properties
logging.use.disruptor=true
logging.buffer.size=8192
logging.event.buffer.size=16384
```

Increase buffer sizes if you see dropped log events under very high
operation counts (> 50 000 ops).

**Note on Clojure warnings.** Clojure runtime initialization produces
`INFO`-level warnings on first load (e.g., `WARNING: clojure.lang.Var`).
These are harmless. Suppress them by setting the `org.clojure` logger
to `WARN` in `log4j2.xml`.

### 1.3 Verification Timeout

For non-linearizable implementations, the JIT-Lin checker may not
terminate within a reasonable time because it must explore all possible
linearizations. To add a per-run timeout, use `withTimeout(Duration)`:

```java
VerificationResult result = VerificationFramework
    .verify("phd.distributed.verifier.PartialSyncQueue")
    .withThreads(4)
    .withOperations(60)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withTimeout(java.time.Duration.ofMinutes(30))
    .run();
```

If the timeout expires, `run()` throws a runtime exception. For batch
experiments, wrap in a try-catch and treat timeouts as errors.

Alternatively, set the default timeout globally in `system.properties`:

```properties
system.default.timeout.ms=1800000   # 30 minutes
```

---

## 2. Troubleshooting

### OutOfMemoryError during verification

Reduce operation count or increase heap size:

```bash
java -Xmx16g -cp target/...jar phd.experiments.VerifierBenchmark ...
```

For experiments on the paper's scale (100 000 operations in Table C),
`-Xmx16g` is recommended.

### Verification takes too long (> several minutes)

The JIT-Lin checker is NP-complete. With non-linearizable implementations
and many operations, the checker may exhaust the search space without
finding a witness. Solutions:

- Reduce `VERDICT_OPS` (e.g., from 100 to 60).
- Add a per-run timeout (see Section 1.3).
- Use a severely broken implementation (`BrokenQueue`) instead of a
  subtly broken one (`PartialSyncQueue`) for initial validation.

### AspectJ strategy fails on maps (100% false negatives)

This is expected behavior, not a bug. `@AfterReturning` does not reliably
capture all arguments for multi-argument operations such as
`put(key, value)`. The resulting trace is structurally malformed and the
verifier always rejects it. Use `CollectFAInc` or `CollectRAW` for map
implementations.

### Java version error during build

Ensure Java 21 is active:

```bash
java -version   # should show 21
export JAVA_HOME=/path/to/java21
export PATH=$JAVA_HOME/bin:$PATH
mvn clean package -DskipTests
```

### "Class not found" error

Use the fully qualified class name:

```java
// Wrong
new A("ConcurrentLinkedQueue", "offer", "poll")

// Correct
new A("java.util.concurrent.ConcurrentLinkedQueue", "offer", "poll")
```

### Clojure warnings in log output

Clojure runtime initialization produces warnings on first load (e.g.,
`WARNING: clojure.lang.Var`). These are harmless. Set `org.clojure`
logger to `WARN` in `log4j2.xml` to suppress them.

### Maven build fails with "Failed to delete target/classes"

A Java process from a previous run has a lock on the directory.
Identify and kill it, then delete `target/` manually:

```bash
ps aux | grep java    # find the PID
kill <PID>
rm -rf target/
mvn clean package -DskipTests
```

### Git index.lock error

A previous Git operation was interrupted:

```bash
rm .git/index.lock
git add .
```

---

## 3. Known Limitations

### NP-completeness of linearizability checking

The JIT-Lin checker solves an NP-complete problem. Verdict experiments
are therefore limited to 60–100 operations per run. Instrumentation
(Table C in the paper) handles up to 100 000 operations efficiently,
but verification at that scale is not feasible.

### AspectJ instrumentation and multi-argument operations

The AspectJ baseline strategy (`@AfterReturning`) fails entirely on
map implementations (`ConcurrentHashMap`, `ConcurrentSkipListMap`)
because it does not reliably capture arguments for `put(key, value)`.
`CollectFAInc` and `CollectRAW` are not affected by this limitation.

### Single-threaded verification

The JIT-Lin checker runs on a single thread. Parallel verification
infrastructure (`ParallelVerifier`) exists in the codebase but is not
integrated in the current release.

### PartialSyncQueue detection rate

`PartialSyncQueue` has a subtle race condition that manifests only under
specific interleavings. With 60 operations and 4 threads, detection rates
are 3–16% for `CollectFAInc` and `CollectRAW`. Increasing operation
count improves detection but increases verification time. See
`docs/EXPERIMENTS.md` for the full discussion.

### Maps and the AspectJ baseline

`ConcurrentHashMap` and `ConcurrentSkipListMap` are verified correctly
by `CollectFAInc` and `CollectRAW` but always reported as non-linearizable
by the AspectJ strategy. This is a known instrumentation artifact, not
a property of the implementations. See the paper's Section 4.4 for the
technical explanation.

---

## 4. Extending the Framework

This section explains how to add support for a new data structure type.
Three steps are required: write the sequential specification in Clojure,
register it in `typelin.clj`, and register the implementation class in
`AlgorithmLibrary`.

### 4.1 Write the Sequential Specification

Sequential specifications live in `src/main/clojure/spec/`. Each file
defines the data structure's initial state and a step function that
determines whether a given operation is valid and what the resulting
state is.

The step function receives four arguments:

| Argument | Type | Description |
|---|---|---|
| `state` | any | Current sequential state of the object |
| `op` | string | Method name (e.g., `"push"`) |
| `arg` | any | First argument to the method (or `nil`) |
| `res` | any | Return value observed at runtime |

It must return a map with two keys:

| Key | Type | Description |
|---|---|---|
| `:ok?` | boolean | `true` if the operation is valid in this state |
| `:state` | any | New state after the operation (ignored if `:ok?` is `false`) |

**Worked example: stack** (`src/main/clojure/spec/stack.clj`)

```clojure
(ns spec.stack)

;; State: a Clojure vector acting as a stack (top = last element)

(defn stack-init []
  [])   ; empty stack

(defn stack-step [state op arg res]
  (cond
    ;; push(x) always succeeds; returns true
    (= op "push")
    {:ok?   true
     :state (conj state arg)}

    ;; pop() on non-empty stack: must return the top element
    (and (= op "pop") (seq state))
    {:ok?   (= res (last state))
     :state (pop state)}

    ;; pop() on empty stack: must return null
    (and (= op "pop") (empty? state))
    {:ok?   (nil? res)
     :state state}

    ;; peek() on non-empty stack: must return top without removing
    (and (= op "peek") (seq state))
    {:ok?   (= res (last state))
     :state state}

    ;; peek() on empty stack: must return null
    (and (= op "peek") (empty? state))
    {:ok?   (nil? res)
     :state state}

    ;; unknown operation — reject
    :else
    {:ok? false :state state}))
```

The state can be any Clojure data structure. Because Clojure data
structures are immutable, backtracking in the JIT-Lin checker is
free — the checker simply reuses the previous state reference without
explicitly undoing operations.

**Guidelines for writing specifications:**

- Model the state as the minimal information needed to determine
  correctness. For a queue, a Clojure list suffices. For a map,
  a Clojure hash-map.
- The `:ok?` check must be exact: it compares the observed return
  value `res` against what the sequential specification would produce.
  Use `=` for value equality and `nil?` for null checks.
- Operations that always succeed (e.g., `push`, `offer`) should
  return `:ok? true` regardless of `res`.
- Operations with preconditions (e.g., `pop` on empty stack) should
  model both the satisfied and unsatisfied cases explicitly.

### 4.2 Register the Specification in `typelin.clj`

Open `src/main/clojure/typelin.clj` and add your specification to the
`specs` map. The key is the object type string used in
`withObjectType(...)`.

```clojure
(ns typelin
  (:require [spec.queue  :as queue]
            [spec.deque  :as deque]
            [spec.set    :as set]
            [spec.map    :as map]
            [spec.stack  :as stack]))   ; ← add your require

(def specs
  {"queue" {:init  queue/queue-init
            :step  queue/queue-step}
   "deque" {:init  deque/deque-init
            :step  deque/deque-step}
   "set"   {:init  set/set-init
            :step  set/set-step}
   "map"   {:init  map/map-init
            :step  map/map-step}
   "stack" {:init  stack/stack-init    ; ← add your entry
            :step  stack/stack-step}})
```

After this change, `withObjectType("stack")` will select your
specification for linearizability checking.

### 4.3 Register the Implementation in `AlgorithmLibrary`

Open `src/main/java/phd/distributed/api/AlgorithmLibrary.java` and add
an entry for your implementation class. If no new category exists, you
can add one to the `AlgorithmCategory` enum.

```java
// In AlgorithmLibrary.java

// 1. Add a category if needed (optional)
public enum AlgorithmCategory {
    QUEUES, DEQUES, SETS, MAPS,
    STACKS   // ← new category
}

// 2. Register the implementation in the static initializer
static {
    // ... existing registrations ...

    register(
        "ConcurrentStack",                            // short name
        "com.example.ConcurrentStack",                // fully qualified class
        AlgorithmCategory.STACKS                      // category
    );
}
```

The short name is what you pass to `AlgorithmLibrary.getInfo(String)`
and to `VerificationFramework.verify(String)`.

### 4.4 Use the New Type

After rebuilding (`mvn clean package -DskipTests`), you can verify your
implementation:

```java
VerificationResult result = VerificationFramework
    .verify("com.example.ConcurrentStack")
    .withThreads(4)
    .withOperations(100)
    .withObjectType("stack")         // matches the key in typelin.clj
    .withMethods("push", "pop")      // must be covered by your spec
    .run();

System.out.println("Linearizable: " + result.isLinearizable());
```

You can also add it to `TABLE_E_ALGORITHMS` in `VerifierBenchmark.java`
to include it in the coverage experiment:

```java
new AlgConfig("ConcurrentStack",
    "com.example.ConcurrentStack",
    "stack", true,       // true = expected to be linearizable
    "push", "pop")
```

### 4.5 Write/Read Classification for Workload Patterns

If you want to use `WorkloadPattern` with your new type, add a
write/read classification to the `WorkloadPattern` class. Without this,
the workload generator will not know which methods are writes and which
are reads, and will fall back to uniform random selection.

```java
// In WorkloadPattern.java, in the method classification map:
case "stack":
    writeOps = List.of("push");
    readOps  = List.of("pop", "peek");
    break;
```

---

## 5. References

- Castañeda, A. & Rodríguez, G. V. (2023). Asynchronous wait-free runtime
  verification and enforcement of linearizability. *PODC 2023*, pp. 90–101.
  https://doi.org/10.1145/3583668.3594563

- Castañeda, A. & Rodríguez, G. V. (2026). Asynchronous wait-free runtime
  verification and enforcement of linearizability. *J. ACM 73*.
  https://doi.org/10.1145/3777409

- Rodríguez, G. V. & Castañeda, A. (2024). Towards efficient runtime
  verified linearizable algorithms. *RV 2024*, LNCS 15278, pp. 262–281.
  https://doi.org/10.1007/978-3-031-74234-7_17

- Lowe, G. (2017). Testing for linearizability. *Concurrency and
  Computation: Practice and Experience*, 29(4), e3928.
  https://doi.org/10.1002/cpe.3928

- El-Hokayem, A. & Falcone, Y. (2018). Can we monitor all multithreaded
  programs? *RV 2018*, LNCS 11237, pp. 64–89.

- Georges, A., Buytaert, D. & Eeckhout, L. (2007). Statistically rigorous
  Java performance evaluation. *OOPSLA 2007*, pp. 57–76.
  https://doi.org/10.1145/1297027.1297033

---

## Appendix A: Supported Implementations

All implementations below are registered in `AlgorithmLibrary` and can
be passed to `VerificationFramework.verify(String)` or
`AlgorithmLibrary.getInfo(String)` using the short name.

### Correct implementations (linearizable)

| Short name | Full class | Type |
|---|---|---|
| `ConcurrentLinkedQueue` | `java.util.concurrent.ConcurrentLinkedQueue` | queue |
| `LinkedBlockingQueue` | `java.util.concurrent.LinkedBlockingQueue` | queue |
| `LinkedTransferQueue` | `java.util.concurrent.LinkedTransferQueue` | queue |
| `ConcurrentLinkedDeque` | `java.util.concurrent.ConcurrentLinkedDeque` | deque |
| `LinkedBlockingDeque` | `java.util.concurrent.LinkedBlockingDeque` | deque |
| `ConcurrentSkipListSet` | `java.util.concurrent.ConcurrentSkipListSet` | set |
| `ConcurrentHashMap` | `java.util.concurrent.ConcurrentHashMap` | map |
| `ConcurrentSkipListMap` | `java.util.concurrent.ConcurrentSkipListMap` | map |

### Non-linearizable implementations (for testing detection)

| Short name | Full class | Violation type |
|---|---|---|
| `BrokenQueue` | `phd.distributed.verifier.BrokenQueue` | Severe race condition |
| `NonLinearizableQueue` | `phd.distributed.verifier.NonLinearizableQueue` | FIFO violation |
| `PartialSyncQueue` | `phd.distributed.verifier.PartialSyncQueue` | isEmpty/removeFirst race |

---

## Appendix B: Supported Methods by Object Type

Only the methods listed below are covered by the sequential
specifications in `src/main/clojure/spec/`. Do not pass methods outside
this list to `withMethods(...)`.

### queue
- Write: `offer`, `add`, `put`
- Read: `poll`, `peek`

### deque
- Write: `offerFirst`, `offerLast`, `addFirst`, `addLast`
- Read: `pollFirst`, `pollLast`, `peekFirst`, `peekLast`

### set
- Write: `add`, `remove`
- Read: `contains`

### map
- Write: `put`, `remove`
- Read: `get`, `containsKey`, `containsValue`