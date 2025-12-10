# Efficient Distributed Runtime Verification

A JIT-based linearizability checker for concurrent data structures using undo operations and efficient snapshot collection.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-78%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-21-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## Overview

This tool verifies linearizability of concurrent data structures through:
- **JIT-based checking** with undo operations for efficient backtracking
- **Dual snapshot strategies** (GAI and RAW) for concurrent event collection
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
    .withOperations(100)
    .withObjectType("queue")
    .run();

System.out.println("Linearizable: " + result.isCorrect());
System.out.println("Time: " + result.getExecutionTime().toMillis() + " ms");
```

---

## Features

### Core Capabilities ✅

- **JIT-based Linearizability Checking** - Efficient state space exploration with undo operations
- **Dual Snapshot Strategies** - GAI (Fetch-And-Increment) and RAW (Read-After-Write)
- **Java Concurrent Collections** - Verified support for 9+ standard collections
- **Gavin Lowe's Algorithms** - 40+ concurrent algorithm implementations included
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
- Plus 40+ algorithms from Gavin Lowe's collection

### Architecture

```
┌─────────────────────────────────────┐
│    VerificationFramework (API)     │
│    - Fluent builder pattern         │
└─────────────┬───────────────────────┘
              │
    ┌─────────┴─────────┐
    │                   │
┌───▼────────┐  ┌──────▼──────┐
│ Executioner│  │  Verifier   │
│ - Producers│  │  - JitLin   │
│ - Snapshot │  │  - Checker  │
└───┬────────┘  └──────┬──────┘
    │                   │
    └─────────┬─────────┘
              │
    ┌─────────▼─────────┐
    │  JITLinUndoTester │
    │  - Undo operations│
    │  - State space    │
    └───────────────────┘
```

---

## Documentation

- **[USER_MANUAL.md](USER_MANUAL.md)** - Complete user guide
- **[INSTALL.md](INSTALL.md)** - Installation instructions
- **[API_USAGE_GUIDE.org](API_USAGE_GUIDE.org)** - Detailed API reference
- **[API_EXAMPLES.md](API_EXAMPLES.md)** - Code examples

---

## Examples

See working examples in `src/main/java/`:
- `Test.java` - Basic verification
- `HighPerformanceLinearizabilityTest.java` - Multiple algorithms
- `NonLinearizableTest.java` - Non-linearizable example

Run examples:
```bash
# Basic test
./run-test.sh

# High-performance test
java -cp "target/classes:$(mvn dependency:build-classpath -q)" \
  HighPerformanceLinearizabilityTest
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
@inproceedings{rv2024-jitlin,
  title={Efficient Distributed Runtime Verification with JIT-based Linearizability Checking},
  author={Your Name},
  booktitle={Runtime Verification 2024},
  year={2024}
}
```

---

## Contact

- **Issues:** [GitHub Issues](repository-url/issues)
- **Email:** your.email@example.com

---

## Acknowledgments

- Gavin Lowe for concurrent algorithm implementations
- RV 2024 community for feedback and support
