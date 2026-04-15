package phd.distributed.reactive;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;
import phd.distributed.verifier.PruningStrategy;
import phd.distributed.verifier.VerificationCache;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Tag("unit")
@Tag("fast")
class ReactiveVerifierTest {

    @Test
    void testReactiveVerification() {
        VerificationCache cache = new VerificationCache();
        PruningStrategy pruning = PruningStrategy.getDefault();
        ReactiveVerifier verifier = new ReactiveVerifier(cache, pruning, 4);

        List<Event> events = Arrays.asList(
            new Event(0, "op1", 1),
            new Event(1, "op2", 2)
        );

        StepVerifier.create(verifier.verify(events))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void testCacheHit() {
        VerificationCache cache = new VerificationCache();
        PruningStrategy pruning = PruningStrategy.getDefault();
        ReactiveVerifier verifier = new ReactiveVerifier(cache, pruning, 4);

        List<Event> events = Arrays.asList(new Event(0, "op", 1));

        // First call - cache miss
        verifier.verify(events).block();

        // Second call - cache hit
        StepVerifier.create(verifier.verify(events))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void testVerifyWithTimeout() {
        VerificationCache cache = new VerificationCache();
        PruningStrategy pruning = PruningStrategy.getDefault();
        ReactiveVerifier verifier = new ReactiveVerifier(cache, pruning, 4);

        List<Event> events = Arrays.asList(new Event(0, "op", 1));

        StepVerifier.create(verifier.verifyWithTimeout(events, Duration.ofSeconds(5)))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void testVerifyWithRetry() {
        VerificationCache cache = new VerificationCache();
        PruningStrategy pruning = PruningStrategy.getDefault();
        ReactiveVerifier verifier = new ReactiveVerifier(cache, pruning, 4);

        List<Event> events = Arrays.asList(new Event(0, "op", 1));

        StepVerifier.create(verifier.verifyWithRetry(events, 3))
            .expectNext(true)
            .verifyComplete();
    }
}
