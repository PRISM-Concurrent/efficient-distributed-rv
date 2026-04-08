# Test Optimization Guide

## Test Categories

Tests are organized using JUnit 5 `@Tag` annotations:

### Tag Hierarchy

```
@Tag("unit")      - Unit tests (fast, isolated)
  └─ @Tag("fast")     - Quick unit tests (< 1s)

@Tag("integration") - Integration tests
  └─ @Tag("thorough") - Comprehensive integration tests

@Tag("stress")    - Stress/load tests (long-running)
```

## Running Tests by Category

### Fast Tests Only (Development)
```bash
mvn test -Dgroups="fast"
# Or
mvn test -Dtest.mode=fast -Dgroups="unit"
```

### Thorough Tests (CI/CD)
```bash
mvn test -Dgroups="thorough"
# Or
mvn test -Dtest.mode=thorough
```

### All Tests (Release)
```bash
mvn test
# Or
mvn test -Dtest.mode=stress
```

### Exclude Slow Tests
```bash
mvn test -DexcludedGroups="stress,integration"
```

## Parallel Execution

### Enabled by Default
Tests run in parallel automatically via `junit-platform.properties`:
- Concurrent method execution
- Concurrent class execution
- Dynamic thread allocation

### Disable Parallel Execution
```bash
mvn test -Djunit.jupiter.execution.parallel.enabled=false
```

### Control Thread Count
```bash
mvn test -Djunit.jupiter.execution.parallel.config.fixed.parallelism=8
```

## Test Result Caching

### Enable Caching
```properties
# In system.properties
feature.result.caching=true
```

### Usage in Tests
```java
String signature = "testName-params-hash";
TestResultCache.CachedResult cached = TestResultCache.get(signature);

if (cached != null && !cached.isStale(3600000)) {
    // Use cached result
    return;
}

// Run test
boolean passed = runTest();
TestResultCache.put(signature, passed, duration);
```

## Reduced Iteration Counts

Default iterations are now controlled by test mode:

```properties
# Fast mode (default)
test.mode=fast
test.fast.iterations=100

# Thorough mode
test.mode=thorough
test.thorough.iterations=1000

# Stress mode
test.mode=stress
test.stress.iterations=10000
```

### In Code
```java
int iterations = SystemConfig.getIterations();
```

## Performance Comparison

| Configuration | Duration | Use Case |
|--------------|----------|----------|
| Fast + Parallel | ~5s | Development |
| Thorough + Parallel | ~30s | CI/CD |
| Stress + Sequential | ~5m | Release |

## Best Practices

### 1. Tag Your Tests
```java
@Tag("unit")
@Tag("fast")
@Test
void quickTest() { }

@Tag("integration")
@Tag("thorough")
@Test
void integrationTest() { }

@Tag("stress")
@Test
void stressTest() { }
```

### 2. Enable Parallel Execution for Safe Tests
```java
@Execution(ExecutionMode.CONCURRENT)
class MyTest { }
```

### 3. Use Test Mode Iterations
```java
@Test
void testWithConfiguredIterations() {
    int iterations = SystemConfig.getIterations();
    for (int i = 0; i < iterations; i++) {
        // Test logic
    }
}
```

### 4. Cache Expensive Test Results
```java
@Test
void expensiveTest() {
    String sig = generateSignature();
    TestResultCache.CachedResult cached = TestResultCache.get(sig);

    if (cached != null && !cached.isStale(3600000)) {
        assumeTrue(cached.passed);
        return;
    }

    long start = System.currentTimeMillis();
    boolean passed = runExpensiveTest();
    TestResultCache.put(sig, passed, System.currentTimeMillis() - start);
}
```

## Maven Profiles

### Fast Profile
```bash
mvn test -Pfast
```

### Thorough Profile
```bash
mvn test -Pthorough
```

### Stress Profile
```bash
mvn test -Pstress
```

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run Fast Tests
  run: mvn test -Dgroups="fast"

- name: Run Thorough Tests
  run: mvn test -Dgroups="thorough"
  if: github.event_name == 'pull_request'

- name: Run All Tests
  run: mvn test
  if: github.ref == 'refs/heads/main'
```

## Troubleshooting

### Tests Hanging
- Check for thread safety issues
- Disable parallel execution temporarily
- Increase timeout values

### Flaky Tests
- Add `@Execution(ExecutionMode.SAME_THREAD)`
- Check for shared state
- Use proper synchronization

### Slow Tests
- Add `@Tag("stress")` to exclude from fast runs
- Reduce iteration counts in fast mode
- Enable result caching
