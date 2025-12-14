package phd.distributed.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class VerificationResult {
    private final boolean correct;
    private final Duration executionTime;
    private final Duration producersTime;
    private final Duration verifiersTime;
    private final List<Violation> violations;
    private final ExecutionStatistics statistics;


    public VerificationResult(boolean correct, Duration executionTime, Duration producersTime, Duration verifierTime,
                            List<Violation> violations, ExecutionStatistics statistics) {
        this.correct = correct;
        this.executionTime = executionTime;
        this.violations = violations != null ? violations : new ArrayList<>();
        this.statistics = statistics;
        this.producersTime = producersTime;
        this.verifiersTime = verifierTime;
    }

    public boolean isCorrect() {
        return correct;
    }

    public boolean isLinearizable() {
        return correct;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public Duration getProdExecutionTime() {
        return producersTime;
    }
    public Duration getVerifierExecutionTime() {
        return verifiersTime;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public ExecutionStatistics getStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return String.format("VerificationResult{correct=%s, time=%s, violations=%d}",
            correct, executionTime, violations.size());
    }

    public static class Violation {
        private final String description;
        private final String trace;

        public Violation(String description, String trace) {
            this.description = description;
            this.trace = trace;
        }

        public String getDescription() {
            return description;
        }

        public String getTrace() {
            return trace;
        }

        @Override
        public String toString() {
            return description + (trace != null ? "\n" + trace : "");
        }
    }

    public static class ExecutionStatistics {
        private final long totalOperations;
        private final long eventsProcessed;

        public ExecutionStatistics(long totalOperations, long eventsProcessed) {
            this.totalOperations = totalOperations;
            this.eventsProcessed = eventsProcessed;
        }

        public long getTotalOperations() {
            return totalOperations;
        }

        public long getEventsProcessed() {
            return eventsProcessed;
        }
    }
}
