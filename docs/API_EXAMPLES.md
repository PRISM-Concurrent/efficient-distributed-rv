# API Examples – Efficient Distributed Runtime Verification

This document provides **hands-on, runnable examples** of the public API.
Each example focuses on a specific *use case* rather than API details.

For API semantics and configuration options, see:
- `API_USAGE_GUIDE.org`
- `USER_MANUAL.md`

---

## Contents

1. Minimal one-liner verification
2. Verify a queue with explicit methods
3. Using different snapshot strategies
4. Workload-based verification
5. Read-heavy vs write-heavy workloads
6. Deterministic verification with schedules
7. Detecting a non-linearizable implementation
8. Low-level execution with `Executioner`
9. High-performance batch example

---

## 1. Minimal one-liner verification

**Use case:** sanity check that an implementation is linearizable.

```java
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MinimalExample {
  public static void main(String[] args) {
    VerificationResult result = VerificationFramework
        .verify(ConcurrentLinkedQueue.class)
        .withOperations(100)
        .run();

    System.out.println("Linearizable: " + result.isLinearizable());
  }
}
```

✔ Uses default settings
✔ Suitable for quick smoke tests

## 2. Choosing method subsets (supported)

You may restrict the verification to a *subset* of supported operations using `withMethods(...)`.
This is useful to focus on specific behaviors and keep workloads meaningful.

**Examples:**

- Deque (front-only):
  `withObjectType("deque").withMethods("offerFirst", "pollFirst")`

- Set (updates only):
  `withObjectType("set").withMethods("add", "remove")`

- Map (updates only):
  `withObjectType("map").withMethods("put", "remove")`

**Important:** Do not include methods that are not covered by the sequential specification.
If you do, the checker may fail or report invalid results.


## 3. Using different snapshot strategies

Use case: compare instrumentation strategies.

```java
VerificationResult gaiResult = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withSnapshot("gAIsnap")
    .run();

VerificationResult rawResult = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withSnapshot("rawsnap")
    .run();
```

✔ gAIsnap: lower overhead
✔ rawsnap: more precise causality

## 4. Workload-based verification (producer–consumer)

Use case: realistic enqueue/dequeue behavior.

```java
import phd.distributed.api.WorkloadPattern;

WorkloadPattern pattern =
    WorkloadPattern.producerConsumer(100, 4, 0.7);

VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withWorkload(pattern)
    .run();
```

✔ ~70% writes, ~30% reads
✔ Threads assigned automatically

## 5. Read-heavy vs write-heavy workloads

Use case: stress different access patterns.

Read-heavy set

```java
WorkloadPattern readHeavy =
    WorkloadPattern.readHeavy(200, 4, 0.8);

VerificationResult result = VerificationFramework
    .verify(java.util.concurrent.ConcurrentSkipListSet.class)
    .withThreads(4)
    .withOperations(200)
    .withObjectType("set")
    .withMethods("add", "remove", "contains")
    .withWorkload(readHeavy)
    .run();
```

Write-heavy deque

```java
WorkloadPattern writeHeavy =
    WorkloadPattern.writeHeavy(200, 4, 0.8);

VerificationResult result = VerificationFramework
    .verify(java.util.concurrent.ConcurrentLinkedDeque.class)
    .withThreads(4)
    .withOperations(200)
    .withObjectType("deque")
    .withMethods("offerFirst", "offerLast", "pollFirst", "pollLast")
    .withWorkload(writeHeavy)
    .run();
```

## 6. Deterministic verification with a fixed schedule

Use case: debugging or reproducing a failure.

```java
import phd.distributed.api.A;
import phd.distributed.datamodel.MethodInf;
import phd.distributed.datamodel.OperationCall;

import java.util.*;

A alg = new A(
    java.util.concurrent.ConcurrentLinkedQueue.class.getName(),
    "offer", "poll"
);

MethodInf offer = null, poll = null;
for (MethodInf m : alg.methods()) {
  if (m.getName().equals("offer")) offer = m;
  if (m.getName().equals("poll"))  poll  = m;
}

List<OperationCall> schedule = new ArrayList<>();
schedule.add(new OperationCall(1, offer));
schedule.add(new OperationCall(null, poll));
schedule.add(new OperationCall(2, offer));

VerificationResult result = VerificationFramework
    .verify(java.util.concurrent.ConcurrentLinkedQueue.class)
    .withThreads(3)
    .withOperations(schedule.size())
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .withSchedule(schedule)
    .run();
```

✔ Fully deterministic
✔ Ideal for minimal counterexamples

## 7. Detecting a non-linearizable implementation

Use case: validate that the checker detects violations.

```java
VerificationResult result = VerificationFramework
    .verify("phd.distributed.verifier.BrokenQueue")
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .run();

System.out.println("Linearizable: " + result.isLinearizable());
```

Expected output:

```bash
Linearizable: false
```

## 8. Low-level execution with Executioner

Use case: full control over execution and verification phases.

```java
import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;

DistAlgorithm alg =
    new A("java.util.concurrent.ConcurrentLinkedQueue",
          "offer", "poll");

Executioner exec =
    new Executioner(4, 100, alg, "queue", "gAIsnap");

exec.taskProducers();
boolean linearizable = exec.taskVerifiers();

System.out.println("Linearizable: " + linearizable);
```

## 9. Batch example

Use case: quick benchmarking across multiple implementations.

```java
String[] algorithms = {
  "ConcurrentLinkedQueue",
  "ConcurrentHashMap",
  "ConcurrentSkipListSet"
};

for (String name : algorithms) {
  VerificationResult r = VerificationFramework
      .verify("java.util.concurrent." + name)
      .withThreads(4)
      .withOperations(1000)
      .run();

  System.out.println(name + " → " + r.isLinearizable());
}
```

Notes

- Examples favor clarity over maximal performance.
- All examples use only public APIs.
- For theoretical background and design rationale, see USER_MANUAL.md.
