# Quick Start

## Prerequisites

- Java 21+
- Maven 3.6+

## Build

```bash
git clone https://github.com/PRISM-Concurrent/efficient-distributed-rv
cd efficient-distributed-rv
mvn clean package -DskipTests
```

## Verify a concurrent data structure

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
System.out.println("Verifier time: " + result.getVerifierExecutionTime().toMillis() + " ms");
```

## Detect a non-linearizable implementation

```java
VerificationResult result = VerificationFramework
    .verify("phd.distributed.verifier.BrokenQueue")
    .withThreads(4)
    .withOperations(100)
    .withObjectType("queue")
    .withMethods("offer", "poll")
    .run();

System.out.println("Linearizable: " + result.isLinearizable()); // false
```

## Run from the command line

```bash
java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
     phd.experiments.BatchExecution
```

For more examples see [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md).
For full installation options see [INSTALL.md](INSTALL.md).