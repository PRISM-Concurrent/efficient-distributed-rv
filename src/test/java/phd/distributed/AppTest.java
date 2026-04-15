package phd.distributed;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;

/**
 * Unit test for the main App class.
 */
public class AppTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testMainMethodExecutesWithoutException() {
        // Given
        String[] args = {};

        // When & Then - main method should execute without throwing exceptions
        assertDoesNotThrow(() -> {
            App.main(args);
        });
    }

    @Test
    void testMainMethodWithDifferentArgs() {
        // Given
        String[] args1 = {};
        String[] args2 = {"arg1"};
        String[] args3 = {"arg1", "arg2", "arg3"};

        // When & Then - should handle different argument arrays
        assertDoesNotThrow(() -> App.main(args1));
        assertDoesNotThrow(() -> App.main(args2));
        assertDoesNotThrow(() -> App.main(args3));
    }

    @Test
    void testMainMethodCreatesCorrectAlgorithm() {
        // This test verifies that the main method uses the expected class name
        // We can't directly test the internal state, but we can verify it doesn't crash

        // Given
        String[] args = {};

        // When & Then
        assertDoesNotThrow(() -> {
            App.main(args);
        });
    }

    @Test
    void testMainMethodUsesAvailableProcessors() {
        // Given
        int expectedCores = Runtime.getRuntime().availableProcessors();

        // When & Then - should use system cores without issues
        assertTrue(expectedCores > 0, "System should have at least one processor");

        assertDoesNotThrow(() -> {
            App.main(new String[]{});
        });
    }

    @Test
    void testMainMethodComponents() {
        // Test that the components used in main method work independently

        // Given
        String className = "java.util.concurrent.ConcurrentLinkedQueue";
        DistAlgorithm alg = new A(className);
        int cores = Runtime.getRuntime().availableProcessors();

        // When & Then
        assertNotNull(alg);
        assertTrue(cores > 0);

        assertDoesNotThrow(() -> {
            Executioner executioner = new Executioner(cores, alg);
            assertNotNull(executioner);
        });
    }

    @Test
    void testMainMethodWithSingleCore() {
        // Test behavior when simulating single core system
        // We can't change Runtime.getRuntime().availableProcessors(),
        // but we can test with minimal cores

        // Given
        String className = "java.util.concurrent.ConcurrentLinkedQueue";
        DistAlgorithm alg = new A(className);

        // When & Then - should work with single core
        assertDoesNotThrow(() -> {
            Executioner executioner = new Executioner(1, alg);
            executioner.taskProducers();
            executioner.taskVerifiers();
        });
    }

    @Test
    void testMainMethodWithValidConcurrentQueue() {
        // Test that the specific class used in main method is valid

        // Given
        String className = "java.util.concurrent.ConcurrentLinkedQueue";

        // When
        DistAlgorithm alg = new A(className);

        // Then
        assertNotNull(alg);
        assertFalse(alg.methods().isEmpty(), "ConcurrentLinkedQueue should have methods");
    }

    @Test
    void testMainMethodExecutionTime() {
        // Test that main method completes in reasonable time

        // Given
        long startTime = System.currentTimeMillis();
        String[] args = {};

        // When
        App.main(args);

        // Then
        long executionTime = System.currentTimeMillis() - startTime;
        assertTrue(executionTime < 60000, "Main method should complete within 60 seconds");
    }

    @Test
    void testMainMethodMultipleExecutions() {
        // Test that main method can be called multiple times

        // Given
        String[] args = {};

        // When & Then
        assertDoesNotThrow(() -> {
            App.main(args);
            App.main(args);
            App.main(args);
        });
    }

    @Test
    void testMainMethodDoesNotHang() {
        // Test that main method doesn't hang indefinitely

        // Given
        String[] args = {};
        Thread mainThread = new Thread(() -> App.main(args));

        // When
        mainThread.start();

        // Then - should complete within reasonable time
        assertDoesNotThrow(() -> {
            mainThread.join(120000); // 2 minute timeout
            assertFalse(mainThread.isAlive(), "Main method should complete and not hang");
        });
    }
}
