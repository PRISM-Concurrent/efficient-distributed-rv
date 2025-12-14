import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.util.ArrayList;
import java.util.List;

public class NonLinearizableTest {

    private static final int THREADS    = 4;
    private static final int OPERATIONS = 100;

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Non-Linearizable Algorithm Test                            ║");
        System.out.println("║  Expected result: NOT LINEARIZABLE                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        List<Result> results = new ArrayList<>();

        results.add(runTest(
            "BrokenQueue",
            "Returns incorrect results intermittently"
        ));

        results.add(runTest(
            "NonLinearizableQueue",
            "Breaks FIFO order by design"
        ));

        // Esperar para que el logging asíncrono termine
        waitForLogs();

        printSummary(results);
    }

    // ------------------------------------------------------------
    // Single test execution
    // ------------------------------------------------------------
    private static Result runTest(String logicalName, String description) {

        long producerMs = -1;
        long verifierMs = -1;
        long totalMs    = -1;
        boolean linearizable = false;
        boolean success = false;
        String error = null;

        try {
            AlgorithmLibrary.AlgorithmInfo info =
                AlgorithmLibrary.getInfo(logicalName);

            if (info == null) {
                throw new IllegalArgumentException(
                    "Algorithm not registered: " + logicalName
                );
            }

            Class<?> implClass = info.getImplementationClass();

            VerificationResult result = VerificationFramework
                .verify(implClass)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType("queue")
                .withMethods("offer", "poll")
                .run();

            producerMs = result.getProdExecutionTime().toMillis();
            verifierMs = result.getVerifierExecutionTime().toMillis();
            totalMs    = result.getExecutionTime().toMillis();
            linearizable = result.isLinearizable();
            success = true;

        } catch (Exception e) {
            error = e.getMessage();
        }

        return new Result(
            logicalName,
            description,
            success,
            linearizable,
            producerMs,
            verifierMs,
            totalMs,
            error
        );
    }

    // ------------------------------------------------------------
    // Final summary table
    // ------------------------------------------------------------
    private static void printSummary(List<Result> results) {

        System.out.println("\n=== Non-Linearizable Test Summary ===");
        System.out.println("Threads    : " + THREADS);
        System.out.println("Operations : " + OPERATIONS + "\n");

        System.out.println("┌──────────────────────────┬────────────┬────────────┬────────────┬────────────┐");
        System.out.println("│ Algorithm                │ Producers  │ Verifier   │ Total      │ Result     │");
        System.out.println("├──────────────────────────┼────────────┼────────────┼────────────┼────────────┤");

        int detected = 0;

        for (Result r : results) {
            String status;

            if (!r.success) {
                status = "ERROR";
            } else if (!r.linearizable) {
                status = "NOT LIN";
                detected++;
            } else {
                status = "LINEARIZABLE";
            }

            System.out.printf(
                "│ %-24s │ %10s │ %10s │ %10s │ %-10s │\n",
                r.name,
                formatMs(r.producerMs),
                formatMs(r.verifierMs),
                formatMs(r.totalMs),
                status
            );
        }

        System.out.println("└──────────────────────────┴────────────┴────────────┴────────────┴────────────┘");

        System.out.println("\nSummary:");
        System.out.println("  Correctly detected as NOT linearizable: "
                           + detected + " / " + results.size());
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private static String formatMs(long ms) {
        return (ms < 0) ? "-" : ms + " ms";
    }

    private static void waitForLogs() {
        try {
            Thread.sleep(200);
            System.out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------
    private static class Result {
        final String name;
        final String description;
        final boolean success;
        final boolean linearizable;
        final long producerMs;
        final long verifierMs;
        final long totalMs;
        final String error;

        Result(String name,
               String description,
               boolean success,
               boolean linearizable,
               long producerMs,
               long verifierMs,
               long totalMs,
               String error) {

            this.name = name;
            this.description = description;
            this.success = success;
            this.linearizable = linearizable;
            this.producerMs = producerMs;
            this.verifierMs = verifierMs;
            this.totalMs = totalMs;
            this.error = error;
        }
    }
}