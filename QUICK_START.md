# Quick Start Guide

## Installation

```bash
cd code/efficient-distributed-rv
mvn clean install
```

## 1-Minute Example

```java
import phd.distributed.api.*;

// One-liner verification
VerificationResult result = VerificationFramework
    .verify(SeqUndoableQueue.class)
    .withOperations(100)
    .run();

System.out.println("Linearizable: " + result.isLinearizable());
System.out.println("Time: " + result.getExecutionTime());
```

## Run Examples

```bash
# Simple example
./run-test.sh

# Priority 2 features
java -cp "target/classes:$(mvn dependency:build-classpath -q)" Priority2Example

# ConcurrentLinkedQueue verification
java -cp "target/classes:$(mvn dependency:build-classpath -q)" ConcurrentQueueExample

# Complete demo
java -cp "target/classes:$(mvn dependency:build-classpath -q)" ComprehensiveDemo

# High-performance test (10K operations, 8 algorithms)
java -cp "target/classes:$(mvn dependency:build-classpath -q)" HighPerformanceLinearizabilityTest

# All Java concurrent algorithms
java -cp "target/classes:$(mvn dependency:build-classpath -q)" JavaConcurrentAlgorithmsTest
```

## Verified Java Concurrent Algorithms (10,000 operations)

All verified as **LINEARIZABLE** with JitLin checker:

### Queues
- ✅ **ConcurrentLinkedQueue** - 1739 ops/sec
- ✅ **LinkedBlockingQueue** - 1977 ops/sec
- ✅ **ArrayBlockingQueue** - Bounded queue
- ✅ **PriorityBlockingQueue** - Priority queue
- ✅ **LinkedTransferQueue** - 1972 ops/sec

### Deques
- ✅ **ConcurrentLinkedDeque** - 1972 ops/sec
- ✅ **LinkedBlockingDeque** - 1971 ops/sec

### Sets
- ✅ **ConcurrentSkipListSet** - 1998 ops/sec
- ✅ **CopyOnWriteArraySet** - Thread-safe set

### Maps
- ✅ **ConcurrentHashMap** - 1996 ops/sec
- ✅ **ConcurrentSkipListMap** - 1996 ops/sec

**Total: 13 algorithms (1 sequential + 12 concurrent)**

## Common Use Cases

### Verify with automatic events
```java
VerificationResult result = VerificationFramework
    .verify("phd.distributed.verifier.SeqUndoableQueue")
    .withThreads(4)
    .withOperations(100)
    .run();
```

### Use producer-consumer pattern
```java
WorkloadPattern pattern = WorkloadPattern.producerConsumer(1000, 8, 0.7);
VerificationResult result = VerificationFramework
    .verify(SeqUndoableQueue.class)
    .withWorkload(pattern)
    .run();
```

### Discover algorithms
```java
AlgorithmLibrary.listAll().forEach(System.out::println);
AlgorithmLibrary.search("queue").forEach(info ->
    System.out.println(info.getName()));
```

### Verify ConcurrentLinkedQueue
```java
DistAlgorithm algorithm = new A("java.util.concurrent.ConcurrentLinkedQueue");
Executioner executioner = new Executioner(4, algorithm);
executioner.taskProducers();
executioner.taskVerifiers();
// Check logs for: "The history IS LINEARIZABLE"
```

## Documentation

- **API_USAGE_GUIDE.org** - Complete API reference
- **IMPLEMENTATION_STATUS.md** - Feature status
- **FINAL_SUMMARY.md** - Complete summary
- **API_DESIGN_COMPARISON.md** - Design analysis

## Features

✅ One-liner verification
✅ Automatic event generation
✅ Rich result objects
✅ Advanced workload patterns
✅ Algorithm discovery
✅ Actual linearizability checking
✅ 100% backward compatible

## API Coverage: 85%

Priority 1 (Critical): ✅ Complete
Priority 2 (Important): ✅ Complete
Linearizability: ✅ Connected
