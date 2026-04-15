package phd.distributed.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import java.util.concurrent.ConcurrentLinkedQueue;

@Tag("unit")
@Execution(ExecutionMode.CONCURRENT)
class ExecutionerTest {

    private DistAlgorithm algorithm;
    private Executioner executioner;

    @BeforeEach
    void setUp() {
        algorithm = new A("java.util.concurrent.ConcurrentLinkedQueue");
        executioner = new Executioner(2, algorithm);
    }

    @Test
    @Tag("fast")
    void testExecutionerCreation() {
        // Then
        assertNotNull(executioner);
    }

    @Test
    @Tag("fast")
    void testExecutionerCreationWithDifferentProcessCounts() {
        // When
        Executioner exec1 = new Executioner(1, algorithm);
        Executioner exec4 = new Executioner(4, algorithm);
        Executioner exec8 = new Executioner(8, algorithm);

        // Then
        assertNotNull(exec1);
        assertNotNull(exec4);
        assertNotNull(exec8);
    }

    @Test
    void testTaskProducersCompletes() {
        // When & Then - should complete without hanging
        assertDoesNotThrow(() -> {
            executioner.taskProducers();
        });
    }

    @Test
    void testTaskVerifiersCompletes() {
        // When & Then - should complete without hanging
        assertDoesNotThrow(() -> {
            executioner.taskVerifiers();
        });
    }

    @Test
    void testTaskProducersWithSingleProcess() {
        // Given
        Executioner singleProcessExecutioner = new Executioner(1, algorithm);

        // When & Then
        assertDoesNotThrow(() -> {
            singleProcessExecutioner.taskProducers();
        });
    }

    @Test
    void testTaskProducersWithMultipleProcesses() {
        // Given
        Executioner multiProcessExecutioner = new Executioner(4, algorithm);

        // When & Then
        assertDoesNotThrow(() -> {
            multiProcessExecutioner.taskProducers();
        });
    }

    @Test
    void testSequentialExecution() {
        // When & Then - should be able to run producers then verifiers
        assertDoesNotThrow(() -> {
            executioner.taskProducers();
            executioner.taskVerifiers();
        });
    }

    @Test
    void testMultipleExecutions() {
        // When & Then - should be able to run multiple times
        assertDoesNotThrow(() -> {
            executioner.taskProducers();
            executioner.taskVerifiers();

            executioner.taskProducers();
            executioner.taskVerifiers();
        });
    }

    @Test
    void testWithDifferentAlgorithms() {
        // Given
        DistAlgorithm stringAlgorithm = new A("java.lang.String");
        DistAlgorithm arrayListAlgorithm = new A("java.util.ArrayList");

        Executioner stringExecutioner = new Executioner(2, stringAlgorithm);
        Executioner arrayListExecutioner = new Executioner(2, arrayListAlgorithm);

        // When & Then
        assertDoesNotThrow(() -> {
            stringExecutioner.taskProducers();
            stringExecutioner.taskVerifiers();
        });

        assertDoesNotThrow(() -> {
            arrayListExecutioner.taskProducers();
            arrayListExecutioner.taskVerifiers();
        });
    }

    @Test
    void testWithInvalidAlgorithm() {
        // Given
        DistAlgorithm invalidAlgorithm = new A("non.existent.Class");
        Executioner invalidExecutioner = new Executioner(2, invalidAlgorithm);

        // When & Then - should handle gracefully
        assertDoesNotThrow(() -> {
            invalidExecutioner.taskProducers();
            invalidExecutioner.taskVerifiers();
        });
    }

    @Test
    void testExecutionerWithZeroProcesses() {
        // Given
        Executioner zeroProcessExecutioner = new Executioner(0, algorithm);

        // When & Then - should complete quickly with no processes
        assertDoesNotThrow(() -> {
            zeroProcessExecutioner.taskProducers();
        });
    }

    @Test
    void testTaskProducersTimeout() {
        // Given - this test ensures the executor doesn't hang indefinitely
        long startTime = System.currentTimeMillis();

        // When
        executioner.taskProducers();

        // Then - should complete within reasonable time (much less than the 1 minute timeout)
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 30000, "Task producers should complete within 30 seconds");
    }

    @Test
    void testTaskVerifiersTimeout() {
        // Given - this test ensures the verifier doesn't hang indefinitely
        long startTime = System.currentTimeMillis();

        // When
        executioner.taskVerifiers();

        // Then - should complete within reasonable time (considering the 10 second delay)
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration >= 10000, "Task verifiers should wait at least 10 seconds");
        assertTrue(duration < 20000, "Task verifiers should complete within 20 seconds");
    }

    @Test
    void testConcurrentExecution() {
        // Given
        Executioner exec1 = new Executioner(2, algorithm);
        Executioner exec2 = new Executioner(2, algorithm);

        // When & Then - multiple executioners should be able to run concurrently
        assertDoesNotThrow(() -> {
            Thread t1 = new Thread(() -> {
                exec1.taskProducers();
                exec1.taskVerifiers();
            });

            Thread t2 = new Thread(() -> {
                exec2.taskProducers();
                exec2.taskVerifiers();
            });

            t1.start();
            t2.start();

            try {
                t1.join(30000); // 30 second timeout
                t2.join(30000); // 30 second timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test was interrupted");
            }

            assertFalse(t1.isAlive(), "Thread 1 should have completed");
            assertFalse(t2.isAlive(), "Thread 2 should have completed");
        });
    }

    @Test
    void testExecutionerWithHighProcessCount() {
        // Given - test with high process count (but reasonable for testing)
        int processCount = Runtime.getRuntime().availableProcessors() * 2;
        Executioner highProcessExecutioner = new Executioner(processCount, algorithm);

        // When & Then
        assertDoesNotThrow(() -> {
            highProcessExecutioner.taskProducers();
        });
    }

    @Test
    void testExecutionerReusability() {
        // Given
        Executioner reusableExecutioner = new Executioner(2, algorithm);

        // When & Then - should be able to use the same executioner multiple times
        for (int i = 0; i < 3; i++) {
            final int iteration = i;
            assertDoesNotThrow(() -> {
                reusableExecutioner.taskProducers();
            }, "Iteration " + iteration + " should not throw exception");
        }
    }
}
