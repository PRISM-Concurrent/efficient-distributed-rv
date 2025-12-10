import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

/**
 * High-performance linearizability testing using the alternative snapshot
 * implementation (e.g., "gAIsnap", typically CollectFAInc).
 */
public class HighPerformanceLinearizabilityGAISnapshotTest {

    private static final int OPERATIONS = 10;
    private static final int THREADS = 3;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  High-Performance Linearizability Testing (gAIsnap)         ║");
        System.out.println("║  Operations: " + OPERATIONS + " | Threads: " + THREADS + "                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        TestResult[] results = {
            test("ConcurrentLinkedQueue", "Lock-free queue", "queue",
                 "offer", "poll"),

            test("LinkedBlockingQueue", "Blocking queue", "queue",
                 "offer", "poll")
        };

        printSummary(results);
    }

    private static TestResult test(String algorithmName, String description,
                                   String objectType, String... methods) {

        System.out.println("┌─ " + algorithmName + " (gAIsnap) ─────────────────────────────");
        System.out.println("│  " + description);
        System.out.println("│  Operations: " + OPERATIONS + " | Threads: " + THREADS);

        long start = System.nanoTime();
        boolean success = false;
        boolean linearizable = false;
        String error = null;
        long durationMs = 0L;

        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(algorithmName);
            if (info == null)
                throw new IllegalArgumentException("Algorithm not found: " + algorithmName);

            Class<?> implClass = info.getImplementationClass();

            VerificationResult result = VerificationFramework
                .verify(implClass)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(objectType)
                .withMethods(methods)
                .withSnapshot("gAIsnap")    // ← usar el otro snapshot
                .run();

            durationMs = result.getExecutionTime().toMillis();
            linearizable = result.isLinearizable();
            success = true;

            if (linearizable) {
                System.out.println("│  ✓ Linearizable");
            } else {
                System.out.println("│  ✗ NOT linearizable");
            }

            System.out.println("│  ✓ Completed in " + durationMs + " ms");
            System.out.println("│  ✓ Total verification time: " + durationMs + " ms");

        } catch (Exception e) {
            error = e.getMessage();
            System.out.println("│  ✗ Error: " + error);
        }

        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return new TestResult(algorithmName, description, success,
                              linearizable, durationMs, error);
    }

    private static void printSummary(TestResult[] results) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                 gAIsnap Snapshot Test Summary               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        int passed = 0, failed = 0;
        long totalTime = 0;

        for (TestResult r : results) {
            String status;

            if (!r.success) {
                status = "FAILED";
                failed++;
            } else if (r.linearizable) {
                status = "LINEARIZABLE";
                passed++;
                totalTime += r.durationMs;
            } else {
                status = "NOT LINEARIZABLE";
                failed++;
            }

            System.out.printf("║  %-28s → %-15s (%d ms)\n",
                              r.name, status, r.durationMs);
        }

        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Passed: " + passed + " | Failed: " + failed);
        System.out.println("║  Total time (linearizable only): " + totalTime + " ms");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static class TestResult {
        final String name;
        final String description;
        final boolean success;
        final boolean linearizable;
        final long durationMs;
        final String error;

        TestResult(String name, String description, boolean success,
                   boolean linearizable, long durationMs, String error) {
            this.name = name;
            this.description = description;
            this.success = success;
            this.linearizable = linearizable;
            this.durationMs = durationMs;
            this.error = error;
        }
    }
}