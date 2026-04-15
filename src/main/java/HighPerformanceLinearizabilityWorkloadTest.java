import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

/**
 * High-performance linearizability testing using custom workload patterns.
 * This version uses WorkloadPattern so that Executioner.taskProducersSeed() is used.
 */
public class HighPerformanceLinearizabilityWorkloadTest {

    private static final int OPERATIONS = 10;
    private static final int THREADS = 4;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  High-Performance Linearizability Testing (Workloads)       ║");
        System.out.println("║  Operations: " + OPERATIONS + " | Threads: " + THREADS + "                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        TestResult[] results = {
            // Lock-free queue with producer-consumer workload
            test("ConcurrentLinkedQueue", "Lock-free queue", "queue",
                 WorkloadPattern.producerConsumer(OPERATIONS, THREADS, 0.7),
                 "offer", "poll"),

            // Concurrent hash map with write-heavy workload
            test("ConcurrentHashMap", "Lock-free hash map", "map",
                 WorkloadPattern.writeHeavy(OPERATIONS, THREADS, 0.8),
                 "put", "get", "remove"),

            // Concurrent sorted set with read-heavy workload
            test("ConcurrentSkipListSet", "Concurrent sorted set", "set",
                 WorkloadPattern.readHeavy(OPERATIONS, THREADS, 0.8),
                 "add", "remove", "contains")
        };

        printSummary(results);
    }

    private static TestResult test(String algorithmName, String description,
                                   String objectType,
                                   WorkloadPattern workload,
                                   String... methods) {

        System.out.println("┌─ " + algorithmName + " (workload) ───────────────────────────");
        System.out.println("│  " + description);
        System.out.println("│  Operations: " + OPERATIONS + " | Threads: " + THREADS);
        System.out.println("│  Workload: " + workload.getClass().getSimpleName());

        long start = System.nanoTime();
        boolean success = false;
        boolean linearizable = false;
        String error = null;
        long durationMs = 0L;

        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(algorithmName);
            if (info == null) {
                throw new IllegalArgumentException("Algorithm not found: " + algorithmName);
            }

            Class<?> implClass = info.getImplementationClass();

            VerificationResult result = VerificationFramework
                .verify(implClass)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(objectType)
                .withMethods(methods)
                .withSnapshot("rAwsnap")              // snapshot por defecto estilo RAW
                .withWorkload(workload)               // ← activa taskProducersSeed
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
            System.out.println("│  ✓ Producer+Verifier total time: " +
                               result.getExecutionTime().toMillis() + " ms");

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
        System.out.println("║                    Workload Test Summary                     ║");
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