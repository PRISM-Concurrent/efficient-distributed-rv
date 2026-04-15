package phd.distributed.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Tag("unit")
@Tag("fast")
class VerificationFrameworkTest {

    @Test
    void testAsyncVerification() throws ExecutionException, InterruptedException {
        List<Event> events = Arrays.asList(
            new Event(0, "op1", 1),
            new Event(1, "op2", 2)
        );

        Boolean result = VerificationFramework
            .verify(Object.class)
            .withThreads(4)
            .runAsync(events)
            .get();

        Assertions.assertTrue(result);
    }

    @Test
    void testReactiveVerification() {
        List<Event> events = Arrays.asList(
            new Event(0, "op1", 1),
            new Event(1, "op2", 2)
        );

        StepVerifier.create(
            VerificationFramework
                .verify(Object.class)
                .withThreads(4)
                .runReactive(events)
        )
        .expectNext(true)
        .verifyComplete();
    }
}
