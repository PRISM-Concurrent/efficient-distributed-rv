# Configuration Guide

## Test Modes

Set in `system.properties`:

```properties
# Fast mode (default) - quick feedback
test.mode=fast

# Thorough mode - comprehensive testing
test.mode=thorough

# Stress mode - exhaustive testing
test.mode=stress
```

### Mode Comparison

| Mode     | Iterations | Duration | Use Case                    |
|----------|------------|----------|----------------------------|
| fast     | 100        | < 5s     | Development, quick checks  |
| thorough | 1,000      | < 30s    | CI/CD, pre-commit          |
| stress   | 10,000     | < 5m     | Release validation         |

## Feature Flags

Enable/disable features for gradual rollout:

```properties
# Async logging (production-ready)
feature.async.logging=true

# Parallel verification (experimental)
feature.parallel.verification=false

# Smart state space pruning (experimental)
feature.smart.pruning=false

# Result caching (experimental)
feature.result.caching=false

# Object pooling (experimental)
feature.object.pooling=false
```

### Usage in Code

```java
if (SystemConfig.FEATURES.parallelVerification) {
    // Use parallel verification
} else {
    // Use sequential verification
}

// Or check by name
if (SystemConfig.FEATURES.isEnabled("smart.pruning")) {
    // Use smart pruning
}
```

## Runtime Override

Override any property via JVM arguments:

```bash
# Override test mode
mvn test -Dtest.mode=stress

# Enable feature flag
mvn test -Dfeature.parallel.verification=true

# Multiple overrides
mvn test -Dtest.mode=thorough -Dlogging.use.disruptor=false
```

## Configuration Priority

1. JVM system properties (highest)
2. system.properties file
3. Default values (lowest)

## Quick Reference

```properties
# System
system.thread.pool.size=8
system.default.iterations=1000
system.default.timeout.ms=30000
system.batch.size=100

# Logging
logging.async.enabled=true
logging.use.disruptor=true
logging.buffer.size=8192
logging.event.buffer.size=16384

# Testing
test.mode=fast|thorough|stress

# Features
feature.async.logging=true
feature.parallel.verification=false
feature.smart.pruning=false
feature.result.caching=false
feature.object.pooling=false

# Performance
performance.monitoring.enabled=false
performance.profiling.enabled=false
```
