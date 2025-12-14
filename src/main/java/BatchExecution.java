import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

public class BatchExecution {

    private static final int OPERATIONS = 100;
    private static final int THREADS = 4;

    public static void main(String[] args) {

        TestResult[] results = {
            run("ConcurrentLinkedQueue", "queue",
                "offer", "poll"),

            run("ConcurrentHashMap", "map",
                "put", "get", "remove"),

            run("ConcurrentLinkedDeque", "deque",
                "offerFirst", "offerLast", "pollFirst", "pollLast"),

            run("LinkedBlockingQueue", "queue",
                "offer", "poll"),

            run("ConcurrentSkipListSet", "set",
                "add", "remove", "contains"),

            run("LinkedTransferQueue", "queue",
                "offer", "poll"),

            run("ConcurrentSkipListMap", "map",
                "put", "get", "remove"),

            run("LinkedBlockingDeque", "deque",
                "offerFirst", "offerLast", "pollFirst", "pollLast")
        };

        printSummary(results);
    }

    // ------------------------------------------------------------
    // Run one algorithm (NO printing here)
    // ------------------------------------------------------------
    private static TestResult run(String algorithmName,
                                  String objectType,
                                  String... methods) {

        try {
            AlgorithmLibrary.AlgorithmInfo info =
                AlgorithmLibrary.getInfo(algorithmName);

            if (info == null)
                return TestResult.error(algorithmName, "Not registered");

            VerificationResult r = VerificationFramework
                .verify(info.getImplementationClass())
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(objectType)
                .withMethods(methods)
                .withSnapshot("rawsnap")
                .run();

            long prodMs  = r.getProdExecutionTime().toMillis();
            long verMs   = r.getVerifierExecutionTime().toMillis();
            long totalMs = r.getExecutionTime().toMillis();

            double throughput =
                (totalMs > 0)
                    ? (OPERATIONS * 1000.0) / totalMs
                    : Double.POSITIVE_INFINITY;

            return new TestResult(
                algorithmName,
                r.isLinearizable(),
                prodMs,
                verMs,
                totalMs,
                throughput,
                null
            );

        } catch (Exception e) {
            return TestResult.error(algorithmName, e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Final summary table
    // ------------------------------------------------------------
    private static void printSummary(TestResult[] results) {

        System.out.println("\n=== Batch Execution Summary ===");
        System.out.println("Threads    : " + THREADS);
        System.out.println("Operations : " + OPERATIONS + "\n");

        System.out.println(
            "┌──────────────────────────┬────────────┬────────────┬────────────┬────────────┐");
        System.out.println(
            "│ Algorithm                │ Producers  │ Verifier   │ Total      │ Throughput │");
        System.out.println(
            "├──────────────────────────┼────────────┼────────────┼────────────┼────────────┤");

        int passed = 0;
        long totalTime = 0;

        for (TestResult r : results) {
            if (!r.success) {
                System.out.printf(
                    "│ %-24s │ %-10s │ %-10s │ %-10s │ %-10s │\n",
                    r.name, "ERROR", "-", "-", "-"
                );
                continue;
            }

            if (r.linearizable) {
                passed++;
                totalTime += r.totalMs;
            }

            System.out.printf(
                "│ %-24s │ %8d ms │ %8d ms │ %8d ms │ %8.0f │\n",
                r.name,
                r.prodMs,
                r.verMs,
                r.totalMs,
                r.throughput
            );
        }

        System.out.println(
            "└──────────────────────────┴────────────┴────────────┴────────────┴────────────┘");

        System.out.println("\nSummary:");
        System.out.println("  Linearizable: " + passed + " / " + results.length);
        System.out.println("  Total time (linearizable only): " + totalTime + " ms");
    }

    // ------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------
    private static class TestResult {
        final String name;
        final boolean success;
        final boolean linearizable;
        final long prodMs;
        final long verMs;
        final long totalMs;
        final double throughput;
        final String error;

        private TestResult(String name, boolean linearizable,
                           long prodMs, long verMs, long totalMs,
                           double throughput, String error) {
            this.name = name;
            this.success = true;
            this.linearizable = linearizable;
            this.prodMs = prodMs;
            this.verMs = verMs;
            this.totalMs = totalMs;
            this.throughput = throughput;
            this.error = error;
        }

        static TestResult error(String name, String error) {
            return new TestResult(name, false, -1, -1, -1, -1, error);
        }
    }
}