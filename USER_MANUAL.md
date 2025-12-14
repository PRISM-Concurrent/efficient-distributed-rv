# User Manual - Efficient Distributed Runtime Verification

**Version:** 1.0
**Date:** December 2025
**For:** RV 2024 Artifact Submission

---

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Quick Start](#quick-start)
4. [Core Concepts](#core-concepts)
5. [API Reference](#api-reference)
6. [Examples](#examples)
7. [Configuration](#configuration)
8. [Troubleshooting](#troubleshooting)
9. [Limitations](#limitations)
10. [References](#references)


---

## 1. Introduction

### What is This Tool?

This tool performs runtime verification of linearizability for concurrent
data structures. Linearizability is a correctness condition requiring that each
operation appears to take effect atomically at some point between its invocation
and response, while preserving real-time ordering of non-overlapping operations.

The framework executes concurrent operations, collects the current execution
using lightweight instrumentation, and checks linearizability using a
Just-In-Time (JIT) backtracking algorithm.


### Key Features

- **Two snapshot strategies** for event collection: GAI (Fetch-And-Increment) and RAW (Read-After-Write)
- **JIT-based verification** using undo operations for efficient backtracking
- **Support for Java concurrent collections**
- **Clean fluent API** for easy integration
- **Extensible architecture** for adding new algorithms

### What Makes This Tool Different?

- Uses non-linearizable snapshot objects for instrumentation, that are
sufficient to detect linearizability violations at runtime.
- If the current execution is not linearizable, then the system under inspection is not linearizable
- Avoids heavy state copying during verification via immutable states.
- Focuses on practical verification of existing concurrent libraries.

---

## 2. Installation

### System Requirements

- **Java:** 21 or higher
- **Maven:** 3.x
- **Memory:** 2GB minimum, 4GB recommended
- **OS:** Linux, macOS, or Windows

### Build from Source

```bash
# Clone repository
git clone https://github.com/PRISM-Concurrent/efficient-distributed-rv.git
cd efficient-distributed-rv

# Build
mvn clean install

# Run tests
mvn test
```

### Verify Installation

```bash
# Should show "BUILD SUCCESS"
mvn compile

# Should show "Tests run: 78, Failures: 0, Errors: 0"
mvn test
```

---

## 3. Quick Start

### Example: High-Level API (recommended)

```java
import phd.distributed.api.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HighLevelExample {
  public static void main(String[] args) {
    VerificationResult result = VerificationFramework
        .verify(ConcurrentLinkedQueue.class)
        .withThreads(4)
        .withOperations(100)
        .withObjectType("queue")
        .withMethods("offer", "poll")
        .run();

    System.out.println("Linearizable: " + result.isLinearizable());
  }
}
```

---


## 4. Core Concepts

### Runtime Verification Architecture

The framework follows a classical three-layer structure:

1. Instrumentation – obtain the current execution
2. Monitoring – decide correctness of the execution
3. Specification – define correct sequential behavior

---

## 5. API Reference

See `API_USAGE_GUIDE.org` for the complete API description.

### 5.1 Executioner Class

Main class for verification workflow.

**Constructor:**

```java
Executioner(int threads, int operations, DistAlgorithm algorithm, String objectType)
Executioner(int processes, int operations, DistAlgorithm algorithm,
            String objectType, String snapType)
```

**Parameters:**

- `threads` - Number of concurrent threads
- `operations` - Total operations to execute
- `algorithm` - Algorithm wrapper (use class `A`)
- `objectType` - Type of data structure ("queue", "map", "set", etc.)
- `snapType` - Snapshot strategy ("gaisnap" or "rawsnap", default: "gaisnap")

**Methods:**

- `taskProducers()` - Execute concurrent operations without a workload
- `taskProducersSeed(List<OperationCall> ops)` - Executes a predefined workload.
Thread identifiers are assigned by the Executioner.
- `taskVerifiers()` - Verify linearizability (returns boolean)

### 5.2 VerificationFramework Class

High-level fluent API.

**Static Methods:**

```java
VerificationBuilder verify(Class<?> algorithmClass)
VerificationBuilder verify(String className)
VerificationBuilder verify(Object instance)
```

**Builder Methods:**

```java
withThreads(int threads)
withOperations(int operations)
withObjectType(String type)
withSnapshot(String snapType)
withTimeout(Duration timeout)
run()  // Returns VerificationResult
runAsync()  // Returns CompletableFuture<VerificationResult>
```

### 5.3 VerificationResult Class

Result object with verification details.

**Methods:**

- `isCorrect()` / `isLinearizable()` - Verification result
- `getExecutionTime()` - Total duration of verification
- `getProdExecutionTime()` - Duration of producers
- `getVerifierExecutionTime()` - Duratioin of
- `getStatistics()` - Execution statistics


### 5.4 AlgorithmLibrary Class

Registry of built-in algorithms.

**Methods:**

```java
static List<AlgorithmInfo> listAll()
static List<AlgorithmInfo> byCategory(Category category)
static AlgorithmInfo getInfo(String name)
```

---

## 6. Examples

### Example 1: Verify ConcurrentHashMap

```java
DistAlgorithm algorithm = new A("java.util.concurrent.ConcurrentHashMap",
                                 "put", "get", "remove");
Executioner exec = new Executioner(4, 100, algorithm, "map");
exec.taskProducers();
boolean result = exec.taskVerifiers();
```

### Example 2: Verify ConcurrentSkipListSet

```java
DistAlgorithm algorithm = new A("java.util.concurrent.ConcurrentSkipListSet",
                                 "add", "remove", "contains");
Executioner exec = new Executioner(4, 100, algorithm, "set");
exec.taskProducers();
boolean result = exec.taskVerifiers();
```

### Example 3: Detect Non-Linearizable Implementation

```java
// BrokenQueue is intentionally non-linearizable
DistAlgorithm algorithm = new A("phd.distributed.verifier.BrokenQueue");
Executioner exec = new Executioner(4, 100, algorithm, "queue");
exec.taskProducers();
boolean result = exec.taskVerifiers();
// Should print: false
```

---

## 7. Configuration

### 7.1 Feature Flags

Edit `src/main/resources/system.properties`:

```properties
# Enable/disable features
feature.parallel.verification=false
feature.smart.pruning=false
feature.result.caching=false

# System settings
system.thread.pool.size=8
system.batch.size=100
```

### 7.2 Logging Configuration

Edit `src/main/resources/log4j2.xml` for logging levels.

---

## 8. Troubleshooting

### Problem: OutOfMemoryError

**Solution:** Reduce operations or increase heap size:

```bash
java -Xmx4g -cp ... YourClass
```

### Problem: Tests fail to compile

**Solution:** Ensure Java 21 is installed:

```bash
java -version  # Should show 21 or higher
```

### Problem: Verification takes too long

**Solution:** Reduce operations or threads:

```java
Executioner exec = new Executioner(2, 50, algorithm, "queue");
```

### Problem: "Class not found" error

**Solution:** Use fully qualified class name:

```java
DistAlgorithm algorithm = new A("java.util.concurrent.ConcurrentLinkedQueue");
```

---

## 9. Limitations

### Current Limitations

1. **Sequential verification only**
   - Single-threaded verification of event history

2. **In-memory datasets only**
   - Streaming infrastructure exists but not functional
   - Limited by available memory

3. **No performance benchmarks**
   - No comparison with other tools (AspectJ, etc.)
   - Performance characteristics not formally measured

4. **Limited test coverage**
   - 78 tests cover core functionality
   - Optimization features not tested

### Known Issues

1. **Clojure initialization**
   - Some tests require Clojure runtime initialization
   - May see warnings in logs (can be ignored)

2. **Large state spaces**
   - Very large operations counts (>10,000) may be slow
   - Consider reducing operations for testing

### Future Work

1. **Parallel verification integration**
   - Connect ParallelVerifier to JITLinUndoTester
   - Achieve actual speedup

2. **Reactive streaming integration**
   - Enable large-scale verification
   - Reduce memory footprint

3. **Performance benchmarking**
   - Compare with existing tools
   - Measure and optimize

4. **Extended test suite**
   - Test all features
   - Increase coverage



## 10. References

- **Castañeda, Armando and Rodríguez, Gilde Valeria.** (2023). *Asynchronous Wait-Free Runtime Verification and Enforcement of Linearizability.* In Proceedings of the 2023 ACM Symposium on Principles of Distributed Computing (PODC '23). Association for Computing Machinery, New York, NY, USA, 90–101. [https://doi.org/10.1145/3583668.3594563]

- **Lowe, Gavin.** *Testing for Linearizability.*
  *Concurrency and Computation: Practice and Experience,* Vol. 29, No. 4, Article e3928, 2017.
  ISSN: 1532-0626.
  [https://doi.org/10.1002/cpe.3928](https://doi.org/10.1002/cpe.3928)

- **Rodríguez, Gilde Valeria and Castañeda, Armando** (2024). *Towards Efficient Runtime     Verified Linearizable Algorithms.* In: Ábrahám, E., Abbas, H. (eds) Runtime Verification. RV 2024. Lecture Notes in Computer Science, vol 15191. Springer, Cham. [https://doi.org/10.1007/978-3-031-74234-7_17]


## Appendix A: Supported Algorithms

### Java Concurrent Collections

- ConcurrentLinkedQueue
- ConcurrentHashMap
- ConcurrentLinkedDeque
- LinkedBlockingQueue
- ConcurrentSkipListSet
- LinkedTransferQueue
- ConcurrentSkipListMap
- LinkedBlockingDeque
- ArrayBlockingQueue
- PriorityBlockingQueue
- CopyOnWriteArraySet


## Appendix B: Supported Method Names by Object Type

### Queue Methods
- `offer`, `poll`
- `add`, `remove`, `element`

### Map Methods
- `put`, `get`, `remove`
- `containsKey`, `containsValue`

### Set Methods
- `add`, `remove`, `contains`

### Deque Methods
- `offerFirst`, `offerLast`
- `pollFirst`, `pollLast`
- `peekFirst`, `peekLast`

---

**End of User Manual**
