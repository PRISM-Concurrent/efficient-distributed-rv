# API Examples

Complete examples for all Phase 2 and Phase 3 APIs.

## Table of Contents
1. [Parallel Verification](#parallel-verification)
2. [Batch Processing](#batch-processing)
3. [Caching](#caching)
4. [Pruning Strategies](#pruning-strategies)
5. [Reactive Verification](#reactive-verification)
6. [Streaming Verification](#streaming-verification)
7. [VerificationFramework API](#verificationframework-api)
8. [Performance Monitoring](#performance-monitoring)

## Parallel Verification

### Basic Usage
```java
import phd.distributed.verifier.ParallelVerifier;
import phd.distributed.datamodel.Event;

// Create verifier with 8 threads
ParallelVerifier verifier = new ParallelVerifier(8);

// Verify events asynchronously
List<Event> events = generateEvents();
CompletableFuture<Boolean> result = verifier.verifyAsync(events);

// Wait for result
boolean isValid = result.get();
System.out.println("Valid: " + isValid);

// Cleanup
verifier.shutdown();
```

### With Custom Thread Count
```java
// Match CPU cores
int cores = Runtime.getRuntime().availableProcessors();
ParallelVerifier verifier = new ParallelVerifier(cores);

// Or use more for I/O-bound tasks
ParallelVerifier verifier = new ParallelVerifier(cores * 2);
```

### Batch Verification
```java
ParallelVerifier verifier = new ParallelVerifier(8);

List<CompletableFuture<Boolean>> futures = testCases.stream()
    .map(verifier::verifyAsync)
    .collect(Collectors.toList());

// Wait for all
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> System.out.println("All verified"));
```

## Batch Processing

### Basic Batch Processing
```java
import phd.distributed.core.BatchProcessor;

// Create processor with batch size 100
BatchProcessor<Event> processor = new BatchProcessor<>(100, batch -> {
    System.out.println("Processing batch of " + batch.size());
    writeToDisk(batch);
});

// Add events (automatically batches)
for (Event event : events) {
    processor.add(event);
}

// Flush remaining
processor.flush();
```

### Custom Batch Handler
```java
BatchProcessor<Event> processor = new BatchProcessor<>(50, batch -> {
    // Custom processing
    long start = System.currentTimeMillis();
    processEvents(batch);
    long duration = System.currentTimeMillis() - start;
    System.out.printf("Processed %d events in %d ms%n", batch.size(), duration);
});
```

### Variable Batch Sizes
```java
// Small batches for low latency
BatchProcessor<Event> lowLatency = new BatchProcessor<>(10, handler);

// Large batches for high throughput
BatchProcessor<Event> highThroughput = new BatchProcessor<>(1000, handler);
```

## Caching

### Basic Caching
```java
import phd.distributed.verifier.VerificationCache;

VerificationCache cache = new VerificationCache();

// Check cache
Optional<CachedResult> cached = cache.get(events);
if (cached.isPresent()) {
    return cached.get().passed();
}

// Verify and cache
long start = System.currentTimeMillis();
boolean result = verify(events);
long duration = System.currentTimeMillis() - start;
cache.put(events, result, duration);
```

### Monitor Cache Effectiveness
```java
VerificationCache cache = new VerificationCache();

// After some usage
double hitRate = cache.getHitRate();
System.out.printf("Cache hit rate: %.1f%%%n", hitRate * 100);

if (hitRate < 0.5) {
    System.err.println("Low cache hit rate - consider cache warming");
}
```

### Cache Warming
```java
// Pre-populate cache with common scenarios
for (List<Event> commonCase : getCommonTestCases()) {
    boolean result = verify(commonCase);
    cache.put(commonCase, result, 0);
}
```

## Pruning Strategies

### Adaptive Pruning (Recommended)
```java
import phd.distributed.verifier.PruningStrategy;
import static phd.distributed.verifier.AdvancedPruningStrategies.*;

// Automatically selects best strategy
PruningStrategy strategy = new AdaptivePruning();
List<Event> pruned = strategy.prune(events);

System.out.printf("Pruned: %d -> %d events (%.1f%% reduction)%n",
    events.size(), pruned.size(),
    100.0 * (events.size() - pruned.size()) / events.size());
```

### Dependency-Aware Pruning
```java
// Keeps first/last per thread + unique operations
PruningStrategy strategy = new DependencyAwarePruning();
List<Event> pruned = strategy.prune(events);
```

### Sampling Pruning
```java
// 50% sampling
PruningStrategy strategy = new SamplingPruning(0.5);
List<Event> pruned = strategy.prune(events);

// 30% sampling for aggressive reduction
PruningStrategy aggressive = new SamplingPruning(0.3);
```

### No Pruning (Debugging)
```java
// Disable pruning for debugging
PruningStrategy strategy = new NoPruning();
List<Event> unpruned = strategy.prune(events); // Returns same list
```

## Reactive Verification

### Basic Reactive Verification
```java
import phd.distributed.reactive.ReactiveVerifier;
import reactor.core.publisher.Mono;

ReactiveVerifier verifier = new ReactiveVerifier(cache, pruning, 8);

Mono<Boolean> result = verifier.verify(events);
result.subscribe(
    isValid -> System.out.println("Valid: " + isValid),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("Complete")
);
```

### With Timeout
```java
verifier.verifyWithTimeout(events, Duration.ofSeconds(30))
    .subscribe(result -> handleResult(result));
```

### With Retry
```java
verifier.verifyWithRetry(events, 3)
    .subscribe(result -> handleResult(result));
```

### Complete Error Handling
```java
verifier.verify(events)
    .timeout(Duration.ofSeconds(30))
    .retry(3)
    .onErrorResume(error -> {
        logger.error("Verification failed", error);
        return Mono.just(false);
    })
    .subscribe(result -> handleResult(result));
```

## Streaming Verification

### Basic Streaming
```java
import phd.distributed.reactive.StreamingVerifier;
import reactor.core.publisher.Flux;

StreamingVerifier verifier = new StreamingVerifier(1000, 16);

Flux<Event> eventStream = Flux.fromIterable(largeDataset);

verifier.verifyStream(eventStream)
    .subscribe(result -> {
        System.out.printf("Batch: %d events, passed: %b, time: %d ms%n",
            result.eventCount(), result.passed(), result.durationMs());
    });
```

### Get Summary
```java
verifier.verifySummary(eventStream)
    .subscribe(summary -> {
        System.out.printf("Total events: %d%n", summary.getTotalEvents());
        System.out.printf("Passed batches: %d%n", summary.getPassedBatches());
        System.out.printf("Failed batches: %d%n", summary.getFailedBatches());
        System.out.printf("Total time: %d ms%n", summary.getTotalDuration());
        System.out.println("All passed: " + summary.allPassed());
    });
```

### Filter Failed Batches
```java
verifier.verifyStream(eventStream)
    .filter(result -> !result.passed())
    .subscribe(failed -> alertOnFailure(failed));
```

### Process in Windows
```java
verifier.verifyStream(eventStream)
    .window(Duration.ofSeconds(1))
    .flatMap(window -> window.count())
    .subscribe(count -> System.out.println("Batches per second: " + count));
```

## VerificationFramework API

### Simple One-Liner
```java
import phd.distributed.api.VerificationFramework;

// Async mode
Boolean result = VerificationFramework
    .verify(ConcurrentQueue.class)
    .withThreads(8)
    .runAsync(events)
    .get();
```

### Reactive Mode
```java
Mono<Boolean> result = VerificationFramework
    .verify(ConcurrentQueue.class)
    .withThreads(8)
    .runReactive(events);

result.subscribe(isValid -> System.out.println("Valid: " + isValid));
```

### With Custom Configuration
```java
VerificationCache cache = new VerificationCache();
PruningStrategy pruning = new AdaptivePruning();

Mono<Boolean> result = VerificationFramework
    .verify(algorithm)
    .withThreads(16)
    .withCache(cache)
    .withPruning(pruning)
    .runReactive(events);
```

## Performance Monitoring

### Basic Metrics
```java
import phd.distributed.monitoring.PerformanceMetrics;

PerformanceMetrics metrics = PerformanceMetrics.getInstance();

// After some operations
System.out.println(metrics.report());
```

### Custom Metrics
```java
// Increment counter
metrics.incrementCounter("my.operation");

// Record timing
long start = System.nanoTime();
performOperation();
metrics.recordTime("my.operation.time", System.nanoTime() - start);

// Get metrics
long count = metrics.getCounter("my.operation");
long totalTime = metrics.getTime("my.operation.time");
```

### Periodic Reporting
```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    System.out.println(metrics.report());
}, 0, 60, TimeUnit.SECONDS);
```

## Complete Integration Example

```java
import phd.distributed.api.VerificationFramework;
import phd.distributed.reactive.*;
import phd.distributed.verifier.*;
import phd.distributed.monitoring.PerformanceMetrics;

public class CompleteExample {

    public static void main(String[] args) {
        // Setup
        VerificationCache cache = new VerificationCache();
        PruningStrategy pruning = new AdaptivePruning();
        PerformanceMetrics metrics = PerformanceMetrics.getInstance();

        // Small dataset - use parallel verification
        List<Event> smallDataset = generateEvents(10000);

        VerificationFramework
            .verify(ConcurrentQueue.class)
            .withThreads(8)
            .withCache(cache)
            .withPruning(pruning)
            .runAsync(smallDataset)
            .thenAccept(result ->
                System.out.println("Small dataset valid: " + result));

        // Large dataset - use streaming
        Flux<Event> largeDataset = generateEventStream(1000000);
        StreamingVerifier streaming = new StreamingVerifier(1000, 16);

        streaming.verifySummary(largeDataset)
            .subscribe(summary -> {
                System.out.printf("Large dataset: %d events in %d ms%n",
                    summary.getTotalEvents(), summary.getTotalDuration());
                System.out.println("All passed: " + summary.allPassed());
            });

        // Print metrics
        System.out.println("\n" + metrics.report());
        System.out.printf("Cache hit rate: %.1f%%%n", cache.getHitRate() * 100);
    }
}
```

## Summary

All APIs are designed to be:
- ✅ **Simple** - One-line usage for common cases
- ✅ **Flexible** - Configurable for advanced scenarios
- ✅ **Composable** - Combine features as needed
- ✅ **Type-safe** - Compile-time checking
- ✅ **Well-documented** - Comprehensive examples

Choose the API that fits your use case:
- **Small datasets:** ParallelVerifier or VerificationFramework
- **Large datasets:** StreamingVerifier
- **Real-time:** ReactiveVerifier
- **Simple cases:** VerificationFramework one-liner
