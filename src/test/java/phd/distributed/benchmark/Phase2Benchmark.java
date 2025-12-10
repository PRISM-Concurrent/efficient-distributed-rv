package phd.distributed.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.core.BatchProcessor;
import phd.distributed.datamodel.Event;
import phd.distributed.verifier.ParallelVerifier;
import phd.distributed.verifier.PruningStrategy;
import phd.distributed.verifier.VerificationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("benchmark")
class Phase2Benchmark {

    @Test
    void benchmarkParallelVerification() throws ExecutionException, InterruptedException {
        List<Event> events = generateEvents(10000, 8);
        ParallelVerifier verifier = new ParallelVerifier(8);

        long start = System.nanoTime();
        verifier.verifyAsync(events).get();
        long duration = System.nanoTime() - start;

        System.out.printf("Parallel verification: %d events in %.2f ms%n",
            events.size(), duration / 1_000_000.0);

        verifier.shutdown();
    }

    @Test
    void benchmarkBatchProcessing() {
        AtomicInteger batchCount = new AtomicInteger(0);
        BatchProcessor<Event> processor = new BatchProcessor<>(100,
            batch -> batchCount.incrementAndGet());

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            processor.add(new Event(i % 8, "op" + i, i));
        }
        processor.flush();
        long duration = System.nanoTime() - start;

        System.out.printf("Batch processing: 10000 events in %.2f ms (%d batches)%n",
            duration / 1_000_000.0, batchCount.get());
    }

    @Test
    void benchmarkCaching() {
        VerificationCache cache = new VerificationCache();
        List<Event> events = generateEvents(1000, 4);

        // Warm up
        cache.put(events, true, 100);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            cache.get(events);
        }
        long duration = System.nanoTime() - start;

        System.out.printf("Cache lookups: 10000 in %.2f ms (%.2f ns/lookup)%n",
            duration / 1_000_000.0, duration / 10000.0);
    }

    @Test
    void benchmarkPruning() {
        PruningStrategy strategy = PruningStrategy.getDefault();
        List<Event> events = generateEvents(10000, 8);

        long start = System.nanoTime();
        List<Event> pruned = strategy.prune(events);
        long duration = System.nanoTime() - start;

        System.out.printf("Pruning: %d -> %d events in %.2f ms (%.1f%% reduction)%n",
            events.size(), pruned.size(), duration / 1_000_000.0,
            100.0 * (events.size() - pruned.size()) / events.size());
    }

    private List<Event> generateEvents(int count, int threads) {
        List<Event> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(new Event(i % threads, "op" + i, i));
        }
        return events;
    }
}
