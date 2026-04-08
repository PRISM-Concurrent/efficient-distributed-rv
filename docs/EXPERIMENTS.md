# Experiments

This document reports the empirical evaluation of the two collect-based
instrumentation strategies introduced in the paper:

- **GAIsnap** — `CollectFAInc`: records events with a global atomic
  Fetch-And-Increment counter.
- **RAWsnap** — `CollectRAW`: records events using a non-linearizable
  read-after-write collect; imposes only a partial order via view containment.
- **AspectJ** — `AspectJSnapshot` + `LinearizabilityMonitorAspect`: records
  events automatically via bytecode interception (load-time weaving); used as a
  baseline for instrumentation transparency.

All experiments run on the eight Java concurrent collections listed below,
exercising the methods specified in `BatchComparison.ALGORITHMS`.

---

## Machine Configuration

<!-- TODO (author): Fill in after running the experiments on the target machine.
     ProducersBenchmark and BatchComparison both print this at the end. -->

| Field | Value |
|-------|-------|
| OS | <!-- e.g. macOS 14.4 / Linux 6.8 --> |
| CPU | <!-- e.g. Apple M2, 8 cores --> |
| Logical CPUs | <!-- Runtime.getRuntime().availableProcessors() --> |
| RAM | <!-- e.g. 16 GB --> |
| JVM | <!-- e.g. OpenJDK 21.0.3, GraalVM 21 --> |
| Heap | <!-- -Xmx setting, or the value printed by ProducersBenchmark --> |

---

## How to Reproduce

### 1. Build

```bash
mvn clean package -DskipTests
```

### 2. Run BatchComparison (Tables 1–3)

```bash
# GAIsnap + RAWsnap only
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
  -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
  phd.experiments.BatchComparison

# All three strategies (AspectJ requires -javaagent)
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -javaagent:$HOME/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
  -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
  phd.experiments.BatchComparison
```

Default parameters (edit `BatchComparison.java` constants to change):

| Parameter | Value |
|-----------|-------|
| `THREADS` | 4 |
| `OPERATIONS` | 100 |
| `WARMUP_ROUNDS` | 5 |
| `MEASURED_ROUNDS` | 10 |

### 3. Run ProducersBenchmark (Tables A–B)

```bash
# Output to org-mode file
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
  phd.experiments.ProducersBenchmark --format=org

# Output to LaTeX
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
  phd.experiments.ProducersBenchmark --format=tex --output=results/table.tex
```

Default parameters:

| Parameter | Value |
|-----------|-------|
| Algorithm | `ConcurrentLinkedQueue` (offer / poll) |
| `FIXED_THREADS` (Table A) | 4 |
| `OPERATIONS_SWEEP` (Table A) | 100, 500, 1 000, 5 000, 10 000 |
| `FIXED_OPERATIONS` (Table B) | 1 000 |
| `THREADS_SWEEP` (Table B) | 1, 2, 4, 8, nCPUs |
| `WARMUP_ROUNDS` | 5 |
| `MEASURED_ROUNDS` | 10 |

---

## Experiment 1 — Instrumentation Overhead (`BatchComparison`, Table 1)

Measures only the **producer phase** (`taskProducers`): the time spent collecting
the concurrent execution, with no linearizability checking.
This isolates the cost of each instrumentation strategy.

<!-- TODO (author): Paste the output of printInstrumentationTable() here.
     The table is printed to stdout by BatchComparison.
     Replace the placeholder rows with real data. -->

```
Threads: 4  |  Operations: 100  |  Warmup: 5  |  Measured: 10
```

| Algorithm | GAIsnap (ms) | RAWsnap (ms) | AspectJ (ms) | GAI/RAW ratio |
|-----------|-------------|-------------|-------------|---------------|
| ConcurrentLinkedQueue | — | — | — | — |
| ConcurrentHashMap | — | — | — | — |
| ConcurrentLinkedDeque | — | — | — | — |
| LinkedBlockingQueue | — | — | — | — |
| ConcurrentSkipListSet | — | — | — | — |
| LinkedTransferQueue | — | — | — | — |
| ConcurrentSkipListMap | — | — | — | — |
| LinkedBlockingDeque | — | — | — | — |

**Interpretation:**
<!-- TODO (author): Discuss whether GAIsnap or RAWsnap is faster,
     by how much, and whether AspectJ shows a penalty. -->

---

## Experiment 2 — Full Verification Pipeline (`BatchComparison`, Table 2)

Measures the **complete pipeline** (`taskProducers` + `taskVerifiers`):
instrumentation time plus the time spent by the JIT-Lin linearizability checker.

<!-- TODO (author): Paste the output of printFullPipelineTable() here. -->

```
Threads: 4  |  Operations: 100  |  Warmup: 5  |  Measured: 10
```

| Algorithm | GAIsnap prod\|ver\|total (ms) | RAWsnap prod\|ver\|total (ms) | AspectJ prod\|ver\|total (ms) | GAI/RAW |
|-----------|------------------------------|------------------------------|------------------------------|---------|
| ConcurrentLinkedQueue | —\|—\|— | —\|—\|— | —\|—\|— | — |
| ConcurrentHashMap | —\|—\|— | —\|—\|— | —\|—\|— | — |
| ConcurrentLinkedDeque | —\|—\|— | —\|—\|— | —\|—\|— | — |
| LinkedBlockingQueue | —\|—\|— | —\|—\|— | —\|—\|— | — |
| ConcurrentSkipListSet | —\|—\|— | —\|—\|— | —\|—\|— | — |
| LinkedTransferQueue | —\|—\|— | —\|—\|— | —\|—\|— | — |
| ConcurrentSkipListMap | —\|—\|— | —\|—\|— | —\|—\|— | — |
| LinkedBlockingDeque | —\|—\|— | —\|—\|— | —\|—\|— | — |

**Interpretation:**
<!-- TODO (author): Discuss the verifier time relative to the instrumentation time.
     Does RAWsnap's partial order lead to a harder or easier problem for JIT-Lin?
     Does AspectJ's automatic interception introduce any observable overhead? -->

---

## Experiment 3 — Linearizability Verdicts (`BatchComparison`, Table 3)

Checks whether all three strategies **agree** on the linearizability verdict for
each algorithm over all `MEASURED_ROUNDS` runs.
Agreement across strategies is a soundness indicator: disagreement would mean
two strategies observe structurally different histories.

<!-- TODO (author): Paste the output of printVerdictTable() here. -->

```
Threads: 4  |  Operations: 100  |  Measured: 10 runs per strategy
```

| Algorithm | GAIsnap | RAWsnap | AspectJ | AGREE? |
|-----------|---------|---------|---------|--------|
| ConcurrentLinkedQueue | —/10 — | —/10 — | —/10 — | — |
| ConcurrentHashMap | — | — | — | — |
| ConcurrentLinkedDeque | — | — | — | — |
| LinkedBlockingQueue | — | — | — | — |
| ConcurrentSkipListSet | — | — | — | — |
| LinkedTransferQueue | — | — | — | — |
| ConcurrentSkipListMap | — | — | — | — |
| LinkedBlockingDeque | — | — | — | — |

Cells show `k/N LIN` (k runs linearizable out of N), `k/N NON`, or `k/N ?`.
`AGREE?` = `YES ✓` if all three strategies give the same unanimous verdict,
`NO !` if they disagree (soundness concern), `MIXED` if some runs disagree
within a single strategy.

**Interpretation:**
<!-- TODO (author): Are all standard collections correctly identified as
     linearizable?  Do the non-linearizable test implementations (BrokenQueue,
     NonLinearizableQueue) yield consistent NON verdicts?
     Any unexpected disagreement between strategies? -->

---

## Experiment 4 — Instrumentation Scalability (`ProducersBenchmark`)

`ProducersBenchmark` isolates the instrumentation cost completely (no JIT-Lin),
measuring `taskProducers()` time across a sweep of operation counts (Table A)
and thread counts (Table B).

### Table A — Operations Sweep (threads = 4 fixed)

<!-- TODO (author): Paste Table A output from ProducersBenchmark here,
     or embed the generated .org / .tex file. -->

| Ops | GAIsnap mean (ms) | GAIsnap tput (op/ms) | RAWsnap mean (ms) | RAWsnap tput | AspectJ mean (ms) | AspectJ tput |
|-----|------------------|----------------------|------------------|--------------|------------------|--------------|
| 100 | — | — | — | — | — | — |
| 500 | — | — | — | — | — | — |
| 1 000 | — | — | — | — | — | — |
| 5 000 | — | — | — | — | — | — |
| 10 000 | — | — | — | — | — | — |

### Table B — Threads Sweep (operations = 1 000 fixed)

<!-- TODO (author): Paste Table B output from ProducersBenchmark here. -->

| Threads | GAIsnap mean (ms) | GAIsnap tput | RAWsnap mean (ms) | RAWsnap tput | AspectJ mean (ms) | AspectJ tput |
|---------|------------------|--------------|------------------|--------------|------------------|--------------|
| 1 | — | — | — | — | — | — |
| 2 | — | — | — | — | — | — |
| 4 | — | — | — | — | — | — |
| 8 | — | — | — | — | — | — |
| nCPUs | — | — | — | — | — | — |

**Interpretation:**
<!-- TODO (author): How does throughput scale with operation count?
     Is the relationship roughly linear (expected for both strategies)?
     Does concurrency (Table B) improve throughput, and up to how many threads?
     What does this imply about the bottleneck (Clojure state vs. the target
     data structure)? -->

---

## Discussion

<!-- TODO (author): 2–4 paragraphs synthesising the four experiments.
     Suggested structure:

     §1 — Main finding: both GAIsnap and RAWsnap impose negligible overhead
          relative to the target operation cost.  Quantify with numbers from
          the tables.

     §2 — Comparison between strategies: GAIsnap vs RAWsnap trade-offs.
          RAWsnap avoids the atomic counter but requires the view-containment
          sort; when does this matter?

     §3 — AspectJ baseline: what does the comparison between AspectJ and
          the explicit strategies reveal about the cost of instrumentation
          transparency?

     §4 — Verdict agreement: all standard Java collections pass as linearizable;
          both test non-linearizable implementations are caught by all strategies.
          This corroborates the soundness argument in the paper. -->

---

## Notes on the AspectJ Strategy

The AspectJ strategy (`AspectJSnapshot` + `LinearizabilityMonitorAspect`) instruments
`A.apply()` at the bytecode level via AspectJ load-time weaving, intercepting
every call to `java.lang.reflect.Method.invoke()` inside class `phd.distributed.api.A`.

It reuses the same Clojure state (`logrAw`) as RAWsnap and produces an identical
X_E format, so the JIT-Lin verdict is directly comparable.

Running with AspectJ requires the `-javaagent` flag and `--add-opens` on Java 21+:

```bash
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -javaagent:$HOME/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
  -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
  phd.experiments.BatchComparison
```

If the `-javaagent` flag is absent, the aspect is inactive and the AspectJ column
shows the same behaviour as running without any interception (empty history).
