package phd.distributed.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import phd.distributed.datamodel.Event;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@Tag("fast")
class ParallelVerifierTest {

    private ParallelVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new ParallelVerifier(4);
    }

    @AfterEach
    void tearDown() {
        verifier.shutdown();
    }

    @Test
    void testVerifyEmptyList() throws ExecutionException, InterruptedException {
        List<Event> events = Collections.emptyList();
        assertTrue(verifier.verifyAsync(events).get());
    }

    @Test
    void testVerifyValidEvents() throws ExecutionException, InterruptedException {
        List<Event> events = Arrays.asList(
            new Event(0, "op1", 1),
            new Event(1, "op2", 2),
            new Event(0, "op3", 3)
        );
        assertTrue(verifier.verifyAsync(events).get());
    }
}
