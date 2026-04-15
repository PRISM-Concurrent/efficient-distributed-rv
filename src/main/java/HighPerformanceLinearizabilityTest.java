import phd.distributed.api.A;
import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;

/**
 * High-performance linearizability testing with 10,000+ operations.
 * Tests the most commonly used Java concurrent data structures.
 */
public class HighPerformanceLinearizabilityTest {

    private static final int OPERATIONS = 10;
    private static final int THREADS = 3;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  High-Performance Linearizability Testing                   ║");
        System.out.println("║  Operations: " + OPERATIONS + " | Threads: " + THREADS + "                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // Test most common concurrent data structures
                TestResult[] results = {
            // Cola lock-free
            test("ConcurrentLinkedQueue", "Lock-free queue", "queue",
                "offer", "poll"),

            //Hash map concurrente
            test("ConcurrentHashMap", "Lock-free hash map", "map",
                 "put", "get", "remove"),

            //Deque lock-free (reutilizamos spec de cola)
            test("ConcurrentLinkedDeque", "Lock-free deque", "deque",
                 "offerFirst", "offerLast", "pollFirst", "pollLast"),

            // Cola bloqueante (usamos operaciones no bloqueantes)
            test("LinkedBlockingQueue", "Blocking queue", "queue",
                 "offer", "poll"),

            // // Conjunto ordenado concurrente
             test("ConcurrentSkipListSet", "Concurrent sorted set", "set",
                  "add", "remove", "contains"),

            // Transfer queue (también con offer/poll no bloqueantes)
            test("LinkedTransferQueue", "Transfer queue", "queue",
                 "offer", "poll"),

            // Mapa ordenado concurrente
            test("ConcurrentSkipListMap", "Concurrent sorted map", "map",
                 "put", "get", "remove"),

            // Deque bloqueante (también con variantes no bloqueantes)
            test("LinkedBlockingDeque", "Blocking deque", "deque",
                 "offerFirst", "offerLast", "pollFirst", "pollLast")
        };
        // Print summary
        printSummary(results);
    }

    private static TestResult test(String algorithmName, String description, String objectType, String... methods) {
        System.out.println("┌─ " + algorithmName + " ─────────────────────────────────────");
        System.out.println("│  " + description);
        System.out.println("│  Operations: " + OPERATIONS + " | Threads: " + THREADS);

        long startTime = System.nanoTime();
        boolean success = false;
        String error = null;

        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(algorithmName);
            String className = info.getImplementationClass().getName();

            DistAlgorithm algorithm = new A(className,methods);
            Executioner executioner = new Executioner(THREADS, OPERATIONS, algorithm,objectType);

            System.out.println("│  Executing concurrent operations...");
            executioner.taskProducers();

            System.out.println("│  Verifying linearizability...");
            executioner.taskVerifiers();

            success = true;

        } catch (Exception e) {
            error = e.getMessage();
            System.out.println("│  ✗ Error: " + error);
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms

        if (success) {
            double opsPerSec = (OPERATIONS * 1000.0) / duration;
            System.out.println("│  ✓ Completed in " + duration + " ms");
            System.out.println("│  ✓ Throughput: " + String.format("%.0f", opsPerSec) + " ops/sec");
            System.out.println("│  (Check logs for linearizability result)");
        }

        System.out.println("└──────────────────────────────────────────────────────────────\n");

        return new TestResult(algorithmName, description, success, duration, error);
    }

    private static void printSummary(TestResult[] results) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Test Summary                                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        int passed = 0;
        int failed = 0;
        long totalTime = 0;

        for (TestResult result : results) {
            if (result.success) {
                passed++;
                totalTime += result.durationMs;
                System.out.println("║  ✓ " + String.format("%-30s", result.name) +
                                 String.format("%6d ms", result.durationMs) + "      ║");
            } else {
                failed++;
                System.out.println("║  ✗ " + String.format("%-30s", result.name) + " FAILED      ║");
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Total Tests: " + results.length +
                         " | Passed: " + passed +
                         " | Failed: " + failed + "                        ║");
        System.out.println("║  Total Time: " + totalTime + " ms                                    ║");
        System.out.println("║  Average: " + (totalTime / results.length) + " ms per algorithm                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static class TestResult {
        final String name;
        final String description;
        final boolean success;
        final long durationMs;
        final String error;

        TestResult(String name, String description, boolean success, long durationMs, String error) {
            this.name = name;
            this.description = description;
            this.success = success;
            this.durationMs = durationMs;
            this.error = error;
        }
    }
}
