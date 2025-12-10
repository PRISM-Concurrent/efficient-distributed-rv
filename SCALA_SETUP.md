# Scala Setup and Usage Guide

## Last Changes (2025-10-09)

### Fixed Issues
1. **Added Scala Maven plugin** to `pom.xml` build configuration
2. **Fixed compilation error** in `LinearizabilityTester.scala` - added type bound `S <: AnyRef`
3. **Created working example** `SimpleExperiments.scala`
4. **Added Java-Scala interop example** `JavaScalaInterop.java`

### Files Modified
- `pom.xml` - Added scala-maven-plugin
- `src/main/scala/lowe/testing/LinearizabilityTester.scala` - Fixed type bounds
- `src/main/scala/SimpleExperiments.scala` - Created (new file)
- `src/main/java/JavaScalaInterop.java` - Created (new file)
- `src/main/java/JavaExperimentsInterop.java` - Created (new file)

## How to Compile and Run

### Compile Everything
```bash
mvn clean compile
```

### Run Scala Experiments
```bash
mvn scala:run -DmainClass=SimpleExperiments
mvn scala:run -DmainClass=LockFreeQueueJITTreeTester
mvn scala:run -DmainClass=ScriptedQueueHistoryTester

### Run Java Application
```bash
mvn exec:java
```

### Run Java-Scala Experiments Interop Example
```bash
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" JavaExperimentsInterop
```

## Java-Scala Interoperability

### Scala Code (SimpleExperiments.scala)
```scala
import ox.cads.util._

object SimpleExperiments {
  def main(args: Array[String]): Unit = {
    Spin(1000) // Scala object method call
  }
}
```

### Java Code (JavaExperimentsInterop.java)
```java
import ox.cads.experiments.Experiments;

public class JavaExperimentsInterop {
  public static void main(String[] args) {
    // Create Scala Params object from Java
    Experiments.Params params = new Experiments.Params(5, 20, 0.05, 0.01);

    // Define measurement function using Scala Function0 interface
    scala.Function0<Object> measurement = new scala.Function0<Object>() {
      @Override
      public Object apply() {
        // Your measurement code here
        return 1000.0; // Return measurement value
      }
    };

    // Call Scala statistical framework from Java
    scala.Tuple2<Object, Object> result =
        Experiments.iterateMeasurement(measurement, params);

    double mean = (Double) result._1();
    double delta = (Double) result._2();
  }
}
```

### Key Interop Points
- **Scala objects** → Java static methods via `.apply()`
- **Scala classes** → Java classes (direct instantiation)
- **Scala case classes** → Java classes with getters
- **Scala functions** → Java functional interfaces (`Function0`, `Function1`, etc.)
- **Scala tuples** → Java objects with `._1()`, `._2()` accessors
- **Both languages** compile to same JVM bytecode

### Advanced Interop Example Results
```
Statistical measurement results:
Mean: 8954.00 ns
Confidence interval: ±439.43 ns
Relative precision: 4.91%
```

## Available Scala Libraries

- **Concurrent Data Structures**: `ox.cads.collection.*`
  - LockFreeStack, LockFreeQueue, LockFreeListSet, etc.
- **Synchronization**: `ox.cads.locks.*`
  - Various lock implementations, semaphores
- **Utilities**: `ox.cads.util.*`
  - Spin, ThreadUtil, Profiler
- **Testing**: `ox.cads.testing.*`
  - Linearizability testers, performance benchmarks
- **Experiments**: `ox.cads.experiments.*`
  - Statistical analysis, confidence intervals

## Status
✅ Scala compilation working
✅ Java compilation working
✅ Java-Scala interop working
✅ Example experiments running
⚠️  Some deprecation warnings (non-blocking)
