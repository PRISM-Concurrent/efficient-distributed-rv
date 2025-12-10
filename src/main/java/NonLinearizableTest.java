import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

public class NonLinearizableTest {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Non-Linearizable Algorithm Test                            ║");
        System.out.println("║  Testing algorithms that violate linearizability            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        System.out.println("This test verifies that the system correctly detects");
        System.out.println("violations of linearizability.\n");

        // Test 1: BrokenQueue
        System.out.println("Test 1: BrokenQueue");
        System.out.println("  • Every 5th offer() returns false even though it succeeds");
        System.out.println("  • Every 7th poll() returns null even when queue has elements\n");
        testAlgorithm("BrokenQueue");

        // Test 2: NonLinearizableQueue
        System.out.println("\nTest 2: NonLinearizableQueue");
        System.out.println("  • Alternates between two internal queues on offer()");
        System.out.println("  • Always polls from first queue, breaking FIFO order");
        System.out.println("  • This DEFINITELY violates linearizability\n");
        testAlgorithm("NonLinearizableQueue");

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Tests Complete - Check logs for verification results       ║");
        System.out.println("║  Expected: Both should be NOT LINEARIZABLE                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static void testAlgorithm(String logicalName) {
        final int THREADS    = 4;
        final int OPERATIONS = 100;

        System.out.println("┌─ Testing " + logicalName + " ────────────────────────────────────");
        System.out.println("│  Operations: " + OPERATIONS + " | Threads: " + THREADS);

        long startTime = System.currentTimeMillis();

        try {
            // 1) Resolver la implementación real desde AlgorithmLibrary
            AlgorithmLibrary.AlgorithmInfo info =
                AlgorithmLibrary.getInfo(logicalName);

            if (info == null) {
                throw new IllegalArgumentException(
                    "Algorithm not registered in AlgorithmLibrary: " + logicalName
                );
            }

            Class<?> implClass = info.getImplementationClass();

            // 2) Usar el nuevo VerificationFramework
            VerificationResult result = VerificationFramework
                .verify(implClass)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType("queue")      // usamos spec de cola
                .withMethods("offer", "poll") // interfaz de queue
                .run();

            long duration = System.currentTimeMillis() - startTime;

            System.out.println("│  Linearizable? " + result.isLinearizable());
            System.out.println("│  Total time   : " + duration + " ms");
        } catch (Exception e) {
            System.out.println("│  Error: " + e.getMessage());
        }

        System.out.println("└──────────────────────────────────────────────────────────────┘");
    }
}