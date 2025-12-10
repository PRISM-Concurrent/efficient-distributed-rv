package phd.distributed.reactive;

import phd.distributed.datamodel.Event;
import phd.distributed.monitoring.PerformanceMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

public class StreamingVerifier {
    private final int batchSize;
    private final int parallelism;
    private final PerformanceMetrics metrics = PerformanceMetrics.getInstance();

    public StreamingVerifier(int batchSize, int parallelism) {
        this.batchSize = batchSize;
        this.parallelism = parallelism;
    }

    public Flux<VerificationResult> verifyStream(Flux<Event> eventStream) {
        return eventStream
            .buffer(batchSize)
            .flatMap(this::verifyBatch, parallelism)
            .doOnNext(r -> metrics.incrementCounter("streaming.batches.verified"));
    }

    private Mono<VerificationResult> verifyBatch(java.util.List<Event> batch) {
        return Mono.fromCallable(() -> {
            long start = System.nanoTime();
            boolean valid = batch.stream().allMatch(e -> e != null && e.getId() >= 0);
            long duration = System.nanoTime() - start;

            metrics.recordTime("streaming.batch.time", duration);
            return new VerificationResult(batch.size(), valid, duration / 1_000_000);
        }).subscribeOn(Schedulers.parallel());
    }

    public Mono<VerificationSummary> verifySummary(Flux<Event> eventStream) {
        return verifyStream(eventStream)
            .reduce(new VerificationSummary(), VerificationSummary::merge);
    }

    public record VerificationResult(int eventCount, boolean passed, long durationMs) {}

    public static class VerificationSummary {
        private int totalEvents = 0;
        private int passedBatches = 0;
        private int failedBatches = 0;
        private long totalDuration = 0;

        public VerificationSummary merge(VerificationResult result) {
            totalEvents += result.eventCount;
            if (result.passed) passedBatches++; else failedBatches++;
            totalDuration += result.durationMs;
            return this;
        }

        public int getTotalEvents() { return totalEvents; }
        public int getPassedBatches() { return passedBatches; }
        public int getFailedBatches() { return failedBatches; }
        public long getTotalDuration() { return totalDuration; }
        public boolean allPassed() { return failedBatches == 0; }
    }
}
