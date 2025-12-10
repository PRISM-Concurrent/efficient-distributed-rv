import java.time.Duration;

import phd.distributed.api.A;
import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.AlgorithmLibrary.AlgorithmInfo;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

/**
 * Comprehensive demonstration of the distributed runtime verification API,
 * using the new VerificationFramework (Executioner + JitLin under the hood).
 */
public class ComprehensiveDemo {

    private static final int DEMO_THREADS    = 4;
    private static final int DEMO_OPERATIONS = 10;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  Distributed Runtime Verification - Complete Demo         ║");
        System.out.println("║  (VerificationFramework + Executioner + JitLin)           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // Priority 1 Features: usar VerificationFramework directamente
        demonstratePriority1();

        // Priority 2 Features: AlgorithmLibrary + patrones de workload
        demonstratePriority2();

        // Actual Linearizability Verification con una concurrent queue real
        demonstrateLinearizabilityCheck();

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  All Features Demonstrated (see logs for details)         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    // ----------------------------------------------------------------------
    // Priority 1: Critical features usando VerificationFramework
    // ----------------------------------------------------------------------
    private static void demonstratePriority1() {
        System.out.println("┌─ Priority 1: Critical Features ───────────────────────────┐");

        // 1. VerificationResult con información básica
        System.out.println("│ 1. VerificationResult - Rich result object");
        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(4)
            .withOperations(50)
            .withTimeout(Duration.ofSeconds(30))
            .withObjectType("queue")            // para typelin
            .withMethods("offer", "poll")       // para A
            .run();
        System.out.println("│    ✓ Linearizable (placeholder flag): " + result.isLinearizable());
        System.out.println("│    ✓ Execution time: " + result.getExecutionTime().toMillis() + " ms");
        System.out.println("│    ✓ Operations: " + result.getStatistics().getTotalOperations());

        // Nota: por ahora correct siempre es true en VerificationFramework,
        // ya que aún no conectamos el resultado de JitLin. Eso lo puedes
        // ajustar más adelante.

        // 2. Configuración con más hilos / operaciones
        System.out.println("│");
        System.out.println("│ 2. Configuration options (threads, ops, timeout)");
        VerificationResult result2 = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(8)
            .withOperations(100)
            .withTimeout(Duration.ofMinutes(10))
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();
        System.out.println("│    ✓ Threads: 8, Operations: 200");
        System.out.println("│    ✓ Time: " + result2.getExecutionTime().toMillis() + " ms");

        System.out.println("└──────────────────────────────────────────────────────────┘\n");
    }

    // ----------------------------------------------------------------------
    // Priority 2: AlgorithmLibrary + WorkloadPattern
    // ----------------------------------------------------------------------
    private static void demonstratePriority2() {
        System.out.println("┌─ Priority 2: Important Features ──────────────────────────┐");

        // 1. AlgorithmLibrary
        System.out.println("│ 1. AlgorithmLibrary - Discovery & Instantiation");
        System.out.println("│    Available algorithms:");
        for (AlgorithmInfo info : AlgorithmLibrary.listAll()) {
            System.out.println("│      • " + info.getName() + " (" + info.getCategory() + ")");
        }

        System.out.println("│");
        System.out.println("│    Search results for 'queue':");
        AlgorithmLibrary.search("queue").forEach(info ->
            System.out.println("│      • " + info.getName()));

        // 2. WorkloadPattern con el nuevo diseño (ScheduledCall)
        System.out.println("│");
        System.out.println("│ 2. Advanced Workload Patterns (producer/consumer, read/write)");
        WorkloadPattern pc =
            WorkloadPattern.producerConsumer(100, 4, 0.7); // 70% productores

        VerificationResult resultPC = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(4)
            .withOperations(10)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withWorkload(pc)         // aquí se usará taskProducersSeed(...)
            .run();
        System.out.println("│    ✓ Producer-Consumer workload executed.");
        System.out.println("│    ✓ Time: " + resultPC.getExecutionTime().toMillis() + " ms");

        System.out.println("└──────────────────────────────────────────────────────────┘\n");
    }

    // ----------------------------------------------------------------------
    // Actual linearizability verification (usar el mismo Framework)
    // ----------------------------------------------------------------------
    private static void demonstrateLinearizabilityCheck() {
        System.out.println("┌─ Actual Linearizability Verification ─────────────────────┐");
        System.out.println("│ Testing: java.util.concurrent.ConcurrentLinkedQueue");
        System.out.println("│  Threads: " + DEMO_THREADS + ", Operations: " + DEMO_OPERATIONS);
        System.out.println("│");

        VerificationResult result = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(DEMO_THREADS)
            .withOperations(DEMO_OPERATIONS)
            .withTimeout(Duration.ofMinutes(10))
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();

        System.out.println("│ ✓ Verification pipeline finished.");
        System.out.println("│   - Linearizable (placeholder flag): " + result.isLinearizable());
        System.out.println("│   - Total time: " + result.getExecutionTime().toMillis() + " ms");
        System.out.println("│   (See logs for JitLin checker output and X_E traces)");
        System.out.println("└──────────────────────────────────────────────────────────┘");
    }
}