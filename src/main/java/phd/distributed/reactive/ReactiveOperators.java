package phd.distributed.reactive;

import phd.distributed.datamodel.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public class ReactiveOperators {

    public static <T> Function<Flux<T>, Flux<T>> withRetry(int maxAttempts) {
        return flux -> flux.retryWhen(
            Retry.backoff(maxAttempts, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(5))
        );
    }

    public static <T> Function<Flux<T>, Flux<T>> withTimeout(Duration timeout) {
        return flux -> flux.timeout(timeout);
    }

    public static <T> Function<Flux<T>, Flux<T>> withCircuitBreaker(int failureThreshold) {
        return flux -> flux.transform(new CircuitBreakerOperator<>(failureThreshold));
    }

    private static class CircuitBreakerOperator<T> implements Function<Flux<T>, Flux<T>> {
        private final int threshold;
        private int failures = 0;
        private boolean open = false;

        CircuitBreakerOperator(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public Flux<T> apply(Flux<T> flux) {
            return flux
                .doOnNext(t -> failures = 0)
                .doOnError(e -> {
                    if (++failures >= threshold) open = true;
                })
                .filter(t -> !open);
        }
    }

    public static Function<Flux<Event>, Flux<List<Event>>> batchWithBackpressure(int batchSize) {
        return flux -> flux
            .buffer(batchSize)
            .onBackpressureBuffer(1000);
    }

    public static <T> Function<Mono<T>, Mono<T>> withFallback(T fallbackValue) {
        return mono -> mono.onErrorReturn(fallbackValue);
    }
}
