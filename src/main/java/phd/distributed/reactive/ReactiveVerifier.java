package phd.distributed.reactive;

import phd.distributed.datamodel.Event;
import phd.distributed.monitoring.PerformanceMetrics;
import phd.distributed.verifier.PruningStrategy;
import phd.distributed.verifier.VerificationCache;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

public class ReactiveVerifier {
    private final VerificationCache cache;
    private final PruningStrategy pruning;
    private final int parallelism;
    private final PerformanceMetrics metrics = PerformanceMetrics.getInstance();

    public ReactiveVerifier(VerificationCache cache, PruningStrategy pruning, int parallelism) {
        this.cache = cache;
        this.pruning = pruning;
        this.parallelism = parallelism;
    }

    public Mono<Boolean> verify(List<Event> events) {
        return Mono.fromCallable(() -> cache.get(events))
            .flatMap(cached -> cached.isPresent()
                ? Mono.just(cached.get().passed())
                    .doOnNext(r -> metrics.incrementCounter("reactive.cache.hits"))
                : verifyWithPruning(events))
            .subscribeOn(Schedulers.parallel())
            .doOnNext(r -> metrics.incrementCounter("reactive.verifications"));
    }

    public Mono<Boolean> verifyWithTimeout(List<Event> events, Duration timeout) {
        return verify(events)
            .timeout(timeout)
            .transform(ReactiveOperators.withFallback(false));
    }

    public Mono<Boolean> verifyWithRetry(List<Event> events, int maxAttempts) {
        return verify(events)
            .retryWhen(reactor.util.retry.Retry.max(maxAttempts));
    }

    private Mono<Boolean> verifyWithPruning(List<Event> events) {
        long start = System.nanoTime();
        return Mono.fromCallable(() -> pruning.prune(events))
            .flatMap(this::verifyEvents)
            .doOnNext(result -> {
                long duration = System.nanoTime() - start;
                cache.put(events, result, duration / 1_000_000);
                metrics.recordTime("reactive.verification.time", duration);
            });
    }

    private Mono<Boolean> verifyEvents(List<Event> events) {
        return Flux.fromIterable(events)
            .parallel(parallelism)
            .runOn(Schedulers.parallel())
            .map(e -> e != null && e.getId() >= 0)
            .sequential()
            .all(b -> b);
    }
}
