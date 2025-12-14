# Efficient Distributed Runtime Verification

A linearizability checker for concurrent data structures that uses non-linearizable snapshot (collect) objects to obtain the current concurrent execution, and checks it using a backtracking-based linearizability testing algorithm.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-78%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-21-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()
[![GitHub](https://img.shields.io/badge/GitHub-public-green)](https://github.com/PRISM-Concurrent/efficient-distributed-rv)

---

## Overview

This tool verifies linearizability of concurrent data structures through:

- **Theoretically analyzed distributed instrumentation** if the current execution is not linearizable, then the system under inspection is not linearizable
- **Two non-linearizable snapshot strategies** (GAI and RAW) used to obtain the current concurrent execution.
- **JIT-based linearizability checking** based on Gavin Lowe’s undo algorithm, enabling efficient backtracking
- **Clean API** for easy integration with Java concurrent collections
- **Extensible architecture** supporting future optimizations

---

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.x

### Installation

```bash
git clone <repository-url>
cd efficient-distributed-rv
mvn clean install
```

### Basic Usage

```java
import phd.distributed.api.*;
import phd.distributed.core.*;

// Create algorithm wrapper
DistAlgorithm algorithm = new A("java.util.concurrent.ConcurrentLinkedQueue");

// Create executioner with 4 threads, 100 operations
Executioner exec = new Executioner(4, 100, algorithm, "queue");

// Run concurrent operations
exec.taskProducers();

// Verify linearizability
boolean isLinearizable = exec.taskVerifiers();
System.out.println("Linearizable: " + isLinearizable);
```

### Using High-Level API

```java
import phd.distributed.api.*;

VerificationResult result = VerificationFramework
    .verify(ConcurrentLinkedQueue.class)
    .withThreads(4)
    .withOperations(50)
    .withObjectType("queue")
    .run();

System.out.println("Linearizable: " + result.isCorrect());
System.out.println("Time: " + result.getExecutionTime().toMillis() + " ms");
```

---

## Features

### Core Capabilities ✅

- **Dual Snapshot Strategies** - GAI (Fetch-And-Increment) and RAW (Read-After-Write)
- **Gavin Lowe's JIT-based Linearizability Checking** - Efficient state space exploration with undo operations
- **Java Concurrent Collections** - Verified support for 9+ standard collections
- **Clean API** - Fluent builder pattern for easy integration

### Verified Algorithms

- `ConcurrentLinkedQueue`
- `ConcurrentHashMap`
- `ConcurrentLinkedDeque`
- `LinkedBlockingQueue`
- `ConcurrentSkipListSet`
- `LinkedTransferQueue`
- `ConcurrentSkipListMap`
- `LinkedBlockingDeque`


### Architecture

![Verification Framework](summary.svg)


---

## Documentation

- **[USER_MANUAL.md](USER_MANUAL.md)** - Complete user guide
- **[INSTALL.md](INSTALL.md)** - Installation instructions
- **[API_USAGE_GUIDE.org](API_USAGE_GUIDE.org)** - Detailed API reference
- **[API_EXAMPLES.md](API_EXAMPLES.md)** - Code examples
- **[QUICK_START.md](QUICK_START.md)** - Ready to execute examples


---

## Examples

See working examples in `src/main/java/`:
- `Test.java` - Basic verification
- `BatchExexcution.java` - Multiple algorithms
- `NonLinearizableTest.java` - Non-linearizable example

Run examples:
```bash
# Basic test
./run-test.sh

# All algorithms test
./run-example BatchExecution
```

---

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=AlgorithmLibraryTest

# Build without tests
mvn package -DskipTests
```

**Test Status:** 78 tests passing

---

## Limitations

- Sequential verification only (parallel infrastructure exists but not integrated)
- Limited to in-memory datasets (streaming infrastructure exists but not functional)
- No performance comparison with other tools yet
- Test coverage focuses on core functionality

---

## Future Work

- Integration of parallel verification infrastructure
- Integration of reactive streaming for large datasets
- Performance benchmarking against existing tools
- Extended test suite for all features
- Web-based visualization

---

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

---

## License

Apache License 2.0 - See LICENSE file for details

---

## Citation

If you use this tool in your research, please cite:

```bibtex
@InProceedings{
    10.1007/978-3-031-74234-7_17,
    author="Rodr{\'i}guez, Gilde Valeria and Casta{\~{n}}eda, Armando",
    editor="{\'A}brah{\'a}m, Erika and Abbas, Houssam",
    title="Towards Efficient Runtime Verified Linearizable Algorithms",
    booktitle="Runtime Verification",
    year="2025",
    publisher="Springer Nature Switzerland",
    address="Cham",
    pages="262--281",
    abstract="An asynchronous, fault-tolerant, sound and complete algorithm for runtime verification of linearizability of concurrent algorithms was proposed in [7]. This solution relies on the snapshot abstraction in distributed computing. The fastest known snapshot algorithms use complex constructions, hard to implement, and the simplest ones provide large step complexity bounds or only weak termination guarantees. Thus, the snapshot-based verification algorithm is not completely satisfactory. In this paper, we propose an alternative solution, based on the collect abstraction, which can be optimally implemented in a simple manner. As a final result, we offer a simple and generic methodology that takes any presumably linearizable algorithm and produces a lightweight runtime verified linearizable version of it.",
    isbn="978-3-031-74234-7"
}
```

---

## Contact

- **Issues:** [GitHub Issues](https://github.com/gilde-valeria/rv_collects/issues) - We're following an internal development flow, where all changes are made and tested in Gilde's repository and then in a public repository.
- **Maintainers:**
  - **Miguel Piña** — `miguelpinia1@gmail.com`
  - **Gilde Valeria Rodríguez** — `gildevroji@gmail.com`


---

## Acknowledgments

- The RV 2024 community for insightful feedback and constructive discussions.
- Gavin Lowe for foundational work on linearizability checking and for making his tools and documentation easily accessible.
