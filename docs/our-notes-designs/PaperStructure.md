# Title: Distributed Runtime Verification of Linearizability: Distributed Instrumentation algorithms

## Authors

- Gilde Valeria Rodríguez (UNAM, Mexico)
- Miguel A. Piña (Mexico)
- Armando Castañeda (UNAM, Mexico)

---

### Abstract

<100–150 words. State the problem, the gap, the solution, and the main contribution of the software.>

---

### Introduction

- Verifying concurrent executions of distributed algorithms at runtime is a complex problem.

#### The paper “Can We Monitor All Multithreaded Programs?” by Falcone and El-Hoyakem explains that a Runtime Verification system is usually divided into three layers

##### Instrumentation Layer

- Goal: This layer injects code inside the distributed algorithm to capture the order of events. Using AspectJ (bytecode instrumentation), the system can detect method invocations, responses, read variable states, and insert code before and after specific points in the program. This allows the user to decide which points of the execution need to be checked.

##### Specification Layer

- Goal: Once the execution trace is obtained, a formal language is used to describe the property to verify. Examples are regular expressions, temporal logic, or parametric properties. From the specification, a monitor is generated.

##### Monitoring Layer

    - Goal: This layer takes the execution and the specification and produces a verdict, saying whether the execution satisfies the specification.

### Motivation

    Falcone and El-Hoyakem show through experiments that AspectJ instrumentation can generate different executions of the same program. Some of these executions are inconsistent with the real execution, which produces false positives and false negatives when checking properties like linearizability.

### Contribution

We present distributed algorithms that can instrument any distributed execution, and we show that they work for common concurrent implementations such as maps, sets, queues, deques, and more.
The main result of our RV 2024 paper is:

- We can verify distributed executions using non-linearizable objects, which makes the instrumentation more efficient.
- Most importantly: If an execution obtained by our distributed instrumentation is not linearizable, then the system under inspection is truly non-linearizable.
This means we avoid the false negatives that appear in the work of Falcone and El-Hoyakem.
- In addition, our method does not interfere with the algorithm under inspection, since we do not inject code into it.

**Technical Contributions:**

- We provide an open-source implementation of our distributed instrumentation algorithms.
- We support asynchronous trace collection using non-linearizable objects (collect objects).
- We reconstruct well-formed histories using the ideas from our RV 2024 paper.
- We make all RV 2024 experiments reproducible and share them as part of this software.

---

### 2. Context

#### 2.1 Runtime Verification and Concurrency

Runtime Verification (RV) checks the correctness of the current execution of a system.
In concurrent algorithms, operations may overlap, so the execution is a partial order, not a simple sequence.

Linearizability is the main correctness condition, but it is difficult to verify.
It was proven to be NP-complete to decide if a single execution is linearizable.
Lowe developed different algorithms using distributed ideas to verify it at runtime, and we use some of those.
(Recent improvements exist, but we leave them for future work.)

#### 2.2 Linearizability and Non-linearizable Objects

In RV 2024, we prove that we can detect the current execution of a distributed algorithm using non-linearizable objects, such as collects.

A collect reads a set of values in a non-consistent way.
This is different from an atomic snapshot, which is designed to return the whole set of values as if they were read in one single moment in time.

Example of a collect behavior:

```java
for i from 0 to n registers
read each array[i]
```

Each read may observe a different moment in time, so the result is not linearizable.
However, in our RV 2024 work we show that this is still enough to detect the execution for verification.

#### 2.3 Summary of the RV 2024 Research Paper

This software accompanies the RV 2024 paper.

In the paper, we present two distributed algorithms to instrument an execution:

##### One with only read and write operations

```code
```

##### One with getAndincrement operations

```code
```

These algorithms allow us to detect the execution of the system under inspection and later verify its linearizability.

---

### 3. Software Overview

Give a high-level architecture of the tool.

#### 3.1 Design Goals

- Asynchronous, non-blocking monitoring.
- No interference with the underlying concurrent program.
- Reconstruct the execution using invocation/return events.
- Provide reproducible deployments and experiments.

#### 3.2 System Architecture

Describe the main components:

- **Wrapper** – records invocations and responses.
- **Verifier** – builds the well-formed history \( X_E \) and views \( W_E \).
- **Collect / CollectRaw** – asynchronous communication objects.
- **Executioner / ThreadPool** – drives the execution of DistAlgorithm.
- **DistAlgorithm interface** – abstraction layer for the data structure being tested.

###### Diagram:  

### 4. Implementation Details

### 5. Reproducibility and Usage

### 6. Evaluation

### 7. Comparison With Existing Tools

### 8. Limitations

### 9. Availability

### 10. Conclusion and Future Work