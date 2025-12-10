package phd.distributed.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import phd.distributed.datamodel.Event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class AsyncEventLoggerTest {

    @Test
    @Tag("fast")
    void testAsyncLogging() throws InterruptedException {
        AsyncEventLogger logger = AsyncEventLogger.getInstance();

        // Log multiple events
        for (int i = 0; i < 100; i++) {
            Event event = new Event(i, "test-operation-" + i, i);
            logger.logEvent(event);
        }

        // Give time for async processing
        Thread.sleep(500);

        // Test passes if no exceptions thrown
        assertTrue(true);
    }

    @Test
    @Tag("thorough")
    void testHighThroughput() throws InterruptedException {
        AsyncEventLogger logger = AsyncEventLogger.getInstance();

        long start = System.nanoTime();

        // Log 10000 events
        for (int i = 0; i < 10000; i++) {
            Event event = new Event(i % 10, "operation-" + i, i);
            logger.logEvent(event);
        }

        long duration = System.nanoTime() - start;

        // Should complete in less than 100ms
        assertTrue(duration < 100_000_000,
            "Logging 10000 events took " + duration / 1_000_000 + "ms");

        Thread.sleep(1000); // Allow processing
    }
}
