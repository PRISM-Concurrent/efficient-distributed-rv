package phd.distributed.reactive;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.stream.IntStream;

@Tag("unit")
@Tag("fast")
class StreamingVerifierTest {

    @Test
    void testStreamingVerification() {
        StreamingVerifier verifier = new StreamingVerifier(100, 4);

        Flux<Event> eventStream = Flux.fromStream(
            IntStream.range(0, 1000)
                .mapToObj(i -> new Event(i % 4, "op" + i, i))
        );

        StepVerifier.create(verifier.verifyStream(eventStream))
            .expectNextCount(10) // 1000 events / 100 batch size
            .verifyComplete();
    }

    @Test
    void testVerificationSummary() {
        StreamingVerifier verifier = new StreamingVerifier(50, 2);

        Flux<Event> eventStream = Flux.fromStream(
            IntStream.range(0, 500)
                .mapToObj(i -> new Event(i % 2, "op" + i, i))
        );

        StepVerifier.create(verifier.verifySummary(eventStream))
            .assertNext(summary -> {
                Assertions.assertEquals(500, summary.getTotalEvents());
                Assertions.assertEquals(10, summary.getPassedBatches());
                Assertions.assertTrue(summary.allPassed());
            })
            .verifyComplete();
    }
}
