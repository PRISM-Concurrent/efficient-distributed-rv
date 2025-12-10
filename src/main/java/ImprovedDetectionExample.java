import phd.distributed.api.*;
import phd.distributed.core.Executioner;

/**
 * Example showing how to improve linearizability error detection
 * with different workload patterns and configurations.
 */
public class ImprovedDetectionExample {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Improved Linearizability Error Detection                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // Show how methods are discovered
        demonstrateMethodDiscovery();

        // Show different workload patterns
        demonstrateWorkloadPatterns();

        // Show stress testing
        demonstrateStressTesting();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Key Takeaways:                                              ║");
        System.out.println("║  1. Methods are auto-discovered via reflection               ║");
        System.out.println("║  2. Different workloads expose different bugs                ║");
        System.out.println("║  3. More operations = higher chance of finding bugs          ║");
        System.out.println("║  4. Targeted patterns test specific scenarios                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static void demonstrateMethodDiscovery() {
        System.out.println("┌─ Method Discovery ───────────────────────────────────────────┐");
        System.out.println("│ Methods are automatically discovered via Java Reflection");
        System.out.println("│");
        System.out.println("│ For ConcurrentLinkedQueue, these methods are tested:");
        System.out.println("│   ✓ offer(E e) - Add element");
        System.out.println("│   ✓ poll() - Remove and return head");
        System.out.println("│   ✓ peek() - Return head without removing");
        System.out.println("│   ✓ size() - Return size");
        System.out.println("│   ✓ isEmpty() - Check if empty");
        System.out.println("│   ✓ add(E e) - Add element");
        System.out.println("│   ✓ remove() - Remove head");
        System.out.println("│");
        System.out.println("│ Excluded methods:");
        System.out.println("│   ✗ Object methods (equals, hashCode, toString)");
        System.out.println("│   ✗ Static methods");
        System.out.println("│   ✗ Thread control (wait, notify)");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");
    }

    private static void demonstrateWorkloadPatterns() {
        System.out.println("┌─ Workload Patterns ──────────────────────────────────────────┐");
        System.out.println("│ Different patterns expose different bugs");
        System.out.println("│");

        // Pattern 1: Uniform (current default)
        System.out.println("│ 1. Uniform Pattern (default)");
        System.out.println("│    Random mix of operations");
        testWithPattern("ConcurrentLinkedQueue",
                       WorkloadPattern.uniform(1000, 4),
                       "Uniform");

        // Pattern 2: Producer-Consumer
        System.out.println("│");
        System.out.println("│ 2. Producer-Consumer Pattern");
        System.out.println("│    70% producers, 30% consumers");
        System.out.println("│    Tests: Heavy insertion with some removal");
        testWithPattern("ConcurrentLinkedQueue",
                       WorkloadPattern.producerConsumer(1000, 4, 0.7),
                       "Producer-Consumer");

        // Pattern 3: Read-Heavy
        System.out.println("│");
        System.out.println("│ 3. Read-Heavy Pattern");
        System.out.println("│    80% reads (peek), 20% writes (offer/poll)");
        System.out.println("│    Tests: Concurrent reads with few modifications");
        testWithPattern("ConcurrentLinkedQueue",
                       WorkloadPattern.readHeavy(1000, 4, 0.8),
                       "Read-Heavy");

        System.out.println("└──────────────────────────────────────────────────────────────┘\n");
    }

    private static void demonstrateStressTesting() {
        System.out.println("┌─ Stress Testing ─────────────────────────────────────────────┐");
        System.out.println("│ More operations = higher chance of finding bugs");
        System.out.println("│");

        // Low stress
        System.out.println("│ 1. Low Stress: 1,000 operations, 4 threads");
        testStress("ConcurrentLinkedQueue", 1000, 4);

        // Medium stress
        System.out.println("│");
        System.out.println("│ 2. Medium Stress: 5,000 operations, 8 threads");
        testStress("ConcurrentLinkedQueue", 5000, 8);

        // High stress
        System.out.println("│");
        System.out.println("│ 3. High Stress: 10,000 operations, 8 threads");
        testStress("ConcurrentLinkedQueue", 10000, 8);

        System.out.println("└──────────────────────────────────────────────────────────────┘\n");
    }

    private static void testWithPattern(String algorithmName,
                                       WorkloadPattern pattern,
                                       String patternName) {
        try {
            long start = System.currentTimeMillis();
            VerificationResult result = VerificationFramework
                .verify("java.util.concurrent." + algorithmName)
                .withWorkload(pattern)
                .run();
            long duration = System.currentTimeMillis() - start;

            System.out.println("│    Result: " + result.isLinearizable() +
                             " (" + duration + " ms)");
        } catch (Exception e) {
            System.out.println("│    Error: " + e.getMessage());
        }
    }

    private static void testStress(String algorithmName, int operations, int threads) {
        try {
            long start = System.currentTimeMillis();
            VerificationResult result = VerificationFramework
                .verify("java.util.concurrent." + algorithmName)
                .withThreads(threads)
                .withOperations(operations)
                .run();
            long duration = System.currentTimeMillis() - start;

            System.out.println("│    Result: " + result.isLinearizable() +
                             " (" + duration + " ms, " +
                             (operations * 1000 / duration) + " ops/sec)");
        } catch (Exception e) {
            System.out.println("│    Error: " + e.getMessage());
        }
    }
}
