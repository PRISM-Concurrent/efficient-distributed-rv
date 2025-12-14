import java.time.Duration;
import java.util.List;

import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.AlgorithmLibrary.AlgorithmInfo;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

/**
 * Comprehensive demonstration of the distributed runtime verification API,
 * using the new VerificationFramework (Executioner + JitLin under the hood).
 *
 * This is an API tour (not a benchmark). For structured comparisons, see:
 * - BatchExecution
 * - SingleImplementationPatternsDemo
 * - SnapshotThroughputComparisonDemo
 */
public class ComprehensiveDemo {

    private static final int DEMO_THREADS = 4;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  Distributed Runtime Verification - Complete Demo         ║");
        System.out.println("║  (VerificationFramework + Executioner + JitLin)           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        Result r1 = demonstrateRichResultAndTimeout();
        Result r2 = demonstrateConfiguration();
        Result r3 = demonstrateAlgorithmLibraryAndWorkload();
        Result r4 = demonstrateEndToEndVerification();

        waitForLogs();

        printSummaryTable(List.of(r1, r2, r3, r4));

        System.out.println("\nDone. (See logs for JitLin checker output and X_E traces.)");
    }

    // ----------------------------------------------------------------------
    // 1) Rich result + timeout + verify(String)
    // ----------------------------------------------------------------------
    private static Result demonstrateRichResultAndTimeout() {
        final int ops = 50;
        final int threads = 4;

        System.out.println("┌─ 1) VerificationResult + timeout + verify(String) ─────────────");

        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(threads)
            .withOperations(ops)
            .withTimeout(Duration.ofSeconds(30))
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();

        System.out.println("│  Linearizable : " + result.isLinearizable());
        System.out.println("│  Producer ms  : " + result.getProdExecutionTime().toMillis());
        System.out.println("│  Verifier ms  : " + result.getVerifierExecutionTime().toMillis());
        System.out.println("│  Total ms     : " + result.getExecutionTime().toMillis());
        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return Result.from("RichResult+Timeout", result);
    }

    // ----------------------------------------------------------------------
    // 2) Configuration options (threads/ops)
    // ----------------------------------------------------------------------
    private static Result demonstrateConfiguration() {
        final int ops = 100;
        final int threads = 8;

        System.out.println("┌─ 2) Configuration options (threads, ops) ─────────────────────");

        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(threads)
            .withOperations(ops)
            .withTimeout(Duration.ofMinutes(10))
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();

        System.out.println("│  Threads      : " + threads);
        System.out.println("│  Operations   : " + ops);
        System.out.println("│  Linearizable : " + result.isLinearizable());
        System.out.println("│  Total ms     : " + result.getExecutionTime().toMillis());
        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return Result.from("ConfigThreadsOps", result);
    }

    // ----------------------------------------------------------------------
    // 3) AlgorithmLibrary discovery + WorkloadPattern
    // ----------------------------------------------------------------------
    private static Result demonstrateAlgorithmLibraryAndWorkload() {
        final int ops = 40;
        final int threads = 4;

        System.out.println("┌─ 3) AlgorithmLibrary + WorkloadPattern ───────────────────────");

        // Keep output short: show only a few entries
        System.out.println("│  Search(\"queue\") sample:");
        AlgorithmLibrary.search("queue").stream().limit(5).forEach(info ->
            System.out.println("│   • " + info.getName())
        );

        WorkloadPattern pc = WorkloadPattern.producerConsumer(ops, threads, 0.7);

        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(threads)
            .withOperations(ops)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withWorkload(pc) // internally uses taskProducersSeed(...)
            .run();

        System.out.println("│  Workload     : producer-consumer (70% writes)");
        System.out.println("│  Linearizable : " + result.isLinearizable());
        System.out.println("│  Total ms     : " + result.getExecutionTime().toMillis());
        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return Result.from("AlgLib+Workload", result);
    }

    // ----------------------------------------------------------------------
    // 4) End-to-end verification (small run)
    // ----------------------------------------------------------------------
    private static Result demonstrateEndToEndVerification() {
        final int ops = 10;
        final int threads = DEMO_THREADS;

        System.out.println("┌─ 4) End-to-end linearizability verification ──────────────────");

        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(threads)
            .withOperations(ops)
            .withTimeout(Duration.ofMinutes(10))
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();

        System.out.println("│  Impl         : ConcurrentLinkedQueue");
        System.out.println("│  Threads/Ops  : " + threads + " / " + ops);
        System.out.println("│  Linearizable : " + result.isLinearizable());
        System.out.println("│  Total ms     : " + result.getExecutionTime().toMillis());
        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return Result.from("EndToEndSmall", result);
    }

    // ----------------------------------------------------------------------
    // Summary table (printed once at the end)
    // ----------------------------------------------------------------------
    private static void printSummaryTable(List<Result> results) {
        System.out.println("=== Comprehensive Demo Summary ===");
        System.out.println("┌──────────────────────┬────────────┬────────────┬────────────┬────────────┐");
        System.out.println("│ Scenario             │ Producers  │ Verifier   │ Total      │ Result     │");
        System.out.println("├──────────────────────┼────────────┼────────────┼────────────┼────────────┤");

        for (Result r : results) {
            System.out.printf(
                "│ %-20s │ %9d ms │ %9d ms │ %9d ms │ %-10s │%n",
                r.name, r.producersMs, r.verifierMs, r.totalMs, (r.linearizable ? "LIN" : "NOT LIN")
            );
        }

        System.out.println("└──────────────────────┴────────────┴────────────┴────────────┴────────────┘");
    }

    private static void waitForLogs() {
        try {
            Thread.sleep(200);
            System.out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Result {
        final String name;
        final long producersMs;
        final long verifierMs;
        final long totalMs;
        final boolean linearizable;

        private Result(String name, long producersMs, long verifierMs, long totalMs, boolean linearizable) {
            this.name = name;
            this.producersMs = producersMs;
            this.verifierMs = verifierMs;
            this.totalMs = totalMs;
            this.linearizable = linearizable;
        }

        static Result from(String name, VerificationResult r) {
            return new Result(
                name,
                r.getProdExecutionTime().toMillis(),
                r.getVerifierExecutionTime().toMillis(),
                r.getExecutionTime().toMillis(),
                r.isLinearizable()
            );
        }
    }
}