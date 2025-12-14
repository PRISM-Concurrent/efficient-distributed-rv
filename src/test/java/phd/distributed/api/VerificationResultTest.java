package phd.distributed.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificationResultTest {

    @Test
    void testSuccessfulResult() {
        VerificationResult.ExecutionStatistics stats =
            new VerificationResult.ExecutionStatistics(100, 100);

        VerificationResult result = new VerificationResult(
            true,
            Duration.ofMillis(50),
            Duration.ofMillis(10),   // producersTime
            Duration.ofMillis(20),   // verifiersTime
            null,                    // violations
            stats
        );

        assertTrue(result.isCorrect());
        assertTrue(result.isLinearizable());
        assertEquals(Duration.ofMillis(50), result.getExecutionTime());

        assertEquals(Duration.ofMillis(10), result.getProdExecutionTime());
        assertEquals(Duration.ofMillis(20), result.getVerifierExecutionTime());

        assertNotNull(result.getViolations());
        assertEquals(0, result.getViolations().size());

        assertEquals(100, result.getStatistics().getTotalOperations());
        assertEquals(100, result.getStatistics().getEventsProcessed());
    }

    @Test
    void testFailedResult() {
        List<VerificationResult.Violation> violations = new ArrayList<>();
        violations.add(new VerificationResult.Violation("Test violation", "trace"));

        VerificationResult.ExecutionStatistics stats =
            new VerificationResult.ExecutionStatistics(100, 100);

        VerificationResult result = new VerificationResult(
            false,
            Duration.ofMillis(50),
            Duration.ofMillis(10),   // producersTime
            Duration.ofMillis(20),   // verifiersTime
            violations,
            stats
        );

        assertFalse(result.isCorrect());
        assertFalse(result.isLinearizable());

        assertEquals(Duration.ofMillis(50), result.getExecutionTime());
        assertEquals(Duration.ofMillis(10), result.getProdExecutionTime());
        assertEquals(Duration.ofMillis(20), result.getVerifierExecutionTime());

        assertEquals(1, result.getViolations().size());
        assertEquals("Test violation", result.getViolations().get(0).getDescription());
        assertEquals("trace", result.getViolations().get(0).getTrace());
    }
}