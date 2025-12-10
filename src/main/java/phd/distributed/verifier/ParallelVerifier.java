package phd.distributed.verifier;

import phd.distributed.config.SystemConfig;
import phd.distributed.datamodel.Event;
import phd.distributed.monitoring.PerformanceMetrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class ParallelVerifier {
    private final ForkJoinPool verificationPool;
    private final int partitionSize;
    private final PerformanceMetrics metrics = PerformanceMetrics.getInstance();

    public ParallelVerifier() {
        this(SystemConfig.DEFAULT_THREAD_POOL_SIZE);
    }

    public ParallelVerifier(int parallelism) {
        this.verificationPool = new ForkJoinPool(parallelism);
        this.partitionSize = Math.max(100, 1000 / parallelism);
    }

    public CompletableFuture<Boolean> verifyAsync(List<Event> events) {
        if (!SystemConfig.FEATURES.parallelVerification) {
            return CompletableFuture.completedFuture(verifySequential(events));
        }

        long start = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            metrics.incrementCounter("parallel.verifications");

            Map<Integer, List<Event>> partitions = events.parallelStream()
                .collect(Collectors.groupingByConcurrent(Event::getId));

            boolean result = partitions.values().parallelStream()
                .allMatch(this::verifyPartition);

            metrics.recordTime("parallel.verification.time", System.nanoTime() - start);
            return result;
        }, verificationPool);
    }

    private boolean verifySequential(List<Event> events) {
        long start = System.nanoTime();
        metrics.incrementCounter("sequential.verifications");
        // Simple validation - actual linearizability checking would require
        // sequential specification and proper event conversion
        boolean result = events.stream().allMatch(e -> e != null);
        metrics.recordTime("sequential.verification.time", System.nanoTime() - start);
        return result;
    }

    private boolean verifyPartition(List<Event> partition) {
        // Validate partition events
        return partition.stream().allMatch(e -> e != null && e.getId() >= 0);
    }

    public void shutdown() {
        verificationPool.shutdown();
    }
}
