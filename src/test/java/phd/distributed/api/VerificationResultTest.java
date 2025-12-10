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
            true, Duration.ofMillis(50), null, stats);

        assertTrue(result.isCorrect());
        assertTrue(result.isLinearizable());
        assertEquals(Duration.ofMillis(50), result.getExecutionTime());
        assertEquals(0, result.getViolations().size());
        assertEquals(100, result.getStatistics().getTotalOperations());
    }

    @Test
    void testFailedResult() {
        List<VerificationResult.Violation> violations = new ArrayList<>();
        violations.add(new VerificationResult.Violation("Test violation", "trace"));

        VerificationResult.ExecutionStatistics stats =
            new VerificationResult.ExecutionStatistics(100, 100);
        VerificationResult result = new VerificationResult(
            false, Duration.ofMillis(50), violations, stats);

        assertFalse(result.isCorrect());
        assertFalse(result.isLinearizable());
        assertEquals(1, result.getViolations().size());
        assertEquals("Test violation", result.getViolations().get(0).getDescription());
    }
}
