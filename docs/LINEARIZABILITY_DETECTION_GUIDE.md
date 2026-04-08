# Linearizability Detection Guide

## How Methods Are Detected for Testing

### Automatic Method Discovery

The system uses **Java Reflection** to automatically discover testable methods:

```java
// In class A (DistAlgorithm implementation)
for (Method method : execClass.getMethods()) {
    // Filter out methods that shouldn't be tested
    if (shouldSkip(method)) continue;

    // Add to testable methods list
    methodList.add(new MethodInf(method));
}
```

### Methods That Are Tested

**Included:**
- ✅ Public instance methods
- ✅ Methods that modify state (`offer`, `poll`, `put`, `get`, etc.)
- ✅ Methods that read state (`peek`, `size`, `isEmpty`, etc.)

**Excluded:**
- ❌ Methods from `Object` class (`equals`, `hashCode`, `toString`)
- ❌ Static methods
- ❌ Thread control methods (`wait`, `notify`, `notifyAll`)
- ❌ Stream methods (`parallelStream`, `spliterator`)

### Example: ConcurrentLinkedQueue

When testing `ConcurrentLinkedQueue`, these methods are automatically discovered:
- `offer(E e)` - Add element
- `poll()` - Remove and return head
- `peek()` - Return head without removing
- `size()` - Return size
- `isEmpty()` - Check if empty
- `add(E e)` - Add element (throws exception if fails)
- `remove()` - Remove head (throws exception if empty)

## How Linearizability Errors Are Detected

### Current System

The system captures **execution traces** and verifies them:

```
1. Execute concurrent operations
   Thread 0: offer(5)  → returns true
   Thread 1: poll()    → returns 5
   Thread 2: offer(10) → returns true

2. Capture execution history (with timestamps)
   {:type :invoke, :tid 0, :op :offer, :arg 5, :time 100}
   {:type :return, :tid 0, :res true, :time 101}
   {:type :invoke, :tid 1, :op :poll, :time 102}
   {:type :return, :tid 1, :res 5, :time 103}
   ...

3. Verify with JitLin checker
   - Try to find a valid sequential ordering
   - Check if all operations can be linearized
   - Report: LINEARIZABLE or NOT LINEARIZABLE
```

### Why Some Bugs Aren't Detected

**Problem**: The current system only tests with **random operations**.

If your buggy code requires a specific sequence to fail:
```java
// Bug only triggers with: offer, offer, poll, poll
// But random execution does: offer, poll, offer, poll
// → Bug never manifests → Test passes
```

## How to Detect More Errors

### Solution 1: Targeted Workload Patterns

Create workloads that specifically test problematic scenarios:

```java
// Test FIFO ordering
WorkloadPattern fifoTest = WorkloadPattern.custom()
    .phase1(() -> {
        // All threads offer in sequence
        for (int i = 0; i < 100; i++) {
            queue.offer(i);
        }
    })
    .phase2(() -> {
        // All threads poll in sequence
        for (int i = 0; i < 100; i++) {
            Object result = queue.poll();
            // Verify FIFO: should get 0, 1, 2, ...
        }
    });
```

### Solution 2: Property-Based Testing

Test specific properties:

```java
// Property: size() should equal offers - polls
int offers = countOffers(history);
int polls = countPolls(history);
int finalSize = queue.size();
assert finalSize == offers - polls;

// Property: poll() should return elements in FIFO order
List<Object> offered = getOfferedElements(history);
List<Object> polled = getPolledElements(history);
assert polled.equals(offered.subList(0, polled.size()));
```

### Solution 3: Stress Testing

Increase operations and contention:

```java
// Current: 1,000 operations, 4 threads
// Stress:  100,000 operations, 32 threads
VerificationResult result = VerificationFramework
    .verify(BrokenQueue.class)
    .withThreads(32)
    .withOperations(100000)
    .run();
```

### Solution 4: Invariant Checking

Add runtime invariant checks:

```java
public class InvariantCheckingQueue<T> {
    private final Queue<T> queue;
    private final AtomicInteger offerCount = new AtomicInteger(0);
    private final AtomicInteger pollCount = new AtomicInteger(0);

    public boolean offer(T item) {
        boolean result = queue.offer(item);
        if (result) {
            offerCount.incrementAndGet();
            checkInvariant();
        }
        return result;
    }

    public T poll() {
        T result = queue.poll();
        if (result != null) {
            pollCount.incrementAndGet();
            checkInvariant();
        }
        return result;
    }

    private void checkInvariant() {
        int size = queue.size();
        int expected = offerCount.get() - pollCount.get();
        if (size != expected) {
            throw new InvariantViolation(
                "Size mismatch: expected " + expected + ", got " + size);
        }
    }
}
```

## Improving Dynamic Workload Detection

### Current Limitation

```java
// Current: Random operations
executioner.taskProducers();  // Random mix of offer/poll
```

### Proposed Enhancement

```java
// Enhanced: Configurable workload patterns
WorkloadConfig config = WorkloadConfig.builder()
    .addPhase("warmup", 1000, uniform())
    .addPhase("stress", 10000, producerConsumer(0.7))
    .addPhase("drain", 1000, consumerOnly())
    .withInvariantChecking(true)
    .withPropertyTesting(true)
    .build();

VerificationResult result = VerificationFramework
    .verify(algorithm)
    .withWorkload(config)
    .run();
```

## Method Selection Strategies

### Strategy 1: All Methods (Current)

Tests all public methods automatically.

**Pros**: Comprehensive
**Cons**: May test irrelevant methods

### Strategy 2: Annotated Methods

```java
@Linearizable
public boolean offer(T item) { ... }

@Linearizable
public T poll() { ... }

// Only test annotated methods
```

### Strategy 3: Method Groups

```java
// Test only mutating methods
VerificationFramework
    .verify(algorithm)
    .withMethodFilter(MethodFilter.MUTATING_ONLY)
    .run();

// Test only specific methods
VerificationFramework
    .verify(algorithm)
    .withMethods("offer", "poll", "peek")
    .run();
```

### Strategy 4: Sequential Specification

```java
// Define which methods to test via sequential spec
public interface QueueSpec<T> {
    @Linearizable boolean offer(T item);
    @Linearizable T poll();
    @Linearizable T peek();
    // size() and isEmpty() not tested
}
```

## Recommended Improvements

### 1. Add Method Filtering

```java
public class VerificationBuilder {
    private Set<String> methodsToTest = null;

    public VerificationBuilder withMethods(String... methods) {
        this.methodsToTest = Set.of(methods);
        return this;
    }

    public VerificationBuilder withMethodFilter(Predicate<Method> filter) {
        // Filter methods based on predicate
        return this;
    }
}
```

### 2. Add Workload Phases

```java
public class WorkloadPattern {
    public static WorkloadPattern phased() {
        return new PhasedWorkload()
            .addPhase("fill", 1000, offers())
            .addPhase("concurrent", 5000, mixed())
            .addPhase("drain", 1000, polls());
    }
}
```

### 3. Add Property Checking

```java
public class VerificationResult {
    private List<PropertyViolation> propertyViolations;

    public boolean checkProperty(Property property) {
        // Check if property holds for execution
    }
}

// Usage
result.checkProperty(Property.FIFO_ORDER);
result.checkProperty(Property.SIZE_CONSISTENCY);
```

### 4. Add Invariant Monitoring

```java
public class VerificationBuilder {
    public VerificationBuilder withInvariants(Invariant... invariants) {
        // Monitor invariants during execution
        return this;
    }
}

// Usage
VerificationFramework
    .verify(algorithm)
    .withInvariants(
        Invariant.sizeConsistency(),
        Invariant.fifoOrder(),
        Invariant.noLostElements()
    )
    .run();
```

## Summary

### Current System
- ✅ Automatic method discovery via reflection
- ✅ Filters out non-testable methods
- ✅ Tests all public instance methods
- ✅ Verifies execution histories with JitLin
- ⚠️ Limited to random workloads
- ⚠️ May miss bugs that need specific sequences

### To Detect More Errors
1. **Targeted workloads** - Test specific scenarios
2. **Property-based testing** - Verify specific properties
3. **Stress testing** - More operations, more threads
4. **Invariant checking** - Runtime consistency checks
5. **Method filtering** - Test only relevant methods
6. **Phased workloads** - Structured test sequences

### Key Insight
**Linearizability verification tests WHAT HAPPENED, not WHAT COULD HAPPEN.**

To find bugs, you need workloads that actually trigger them!
