import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

import java.util.concurrent.ConcurrentLinkedQueue;

public class SingleImplementationPatternsDemo {

    // Parámetros base
    private static final int THREADS    = 4;
    private static final int OPERATIONS = 50;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  Patterns demo for ConcurrentLinkedQueue          ║");
        System.out.println("║  Threads: " + THREADS + " | Operations: " + OPERATIONS + "                ║");
        System.out.println("╚════════════════════════════════════════════════════╝");

        // 1) Sin workload explícito (modo aleatorio interno: chooseOp)
        runScenario("Random (no explicit workload)", null);

        // 2) Patrón productor–consumidor
        WorkloadPattern pcPattern =
            WorkloadPattern.producerConsumer(OPERATIONS, THREADS, 0.7);
        runScenario("Producer-Consumer (70% producers)", pcPattern);

        // 3) Patrón read-heavy
        WorkloadPattern readPattern =
            WorkloadPattern.readHeavy(OPERATIONS, THREADS, 0.8);
        runScenario("Read-heavy (80% reads)", readPattern);

        // 4) Patrón write-heavy
        WorkloadPattern writePattern =
            WorkloadPattern.writeHeavy(OPERATIONS, THREADS, 0.8);
        runScenario("Write-heavy (80% writes)", writePattern);

        System.out.println("\n=== Patterns demo finished ===");
    }

    private static void runScenario(String label, WorkloadPattern pattern) {
        System.out.println("\n┌─ Scenario: " + label + " ─────────────────────────────");

        // Builder base: misma implementación, mismos métodos
        VerificationFramework.VerificationBuilder builder =
            VerificationFramework
                .verify(ConcurrentLinkedQueue.class)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType("queue")          // para typelin
                .withMethods("offer", "poll")     // métodos expuestos por A
                .withSnapshot("gAIsnap");         // o "rAwsnap" si quieres probar RAW

        // Si hay workload, lo usamos; si es null, se usa taskProducers() aleatorio
        if (pattern != null) {
            builder = builder.withWorkload(pattern);
        }

        // Ejecutar verificación
        VerificationResult result = builder.run();

        System.out.println("│  Linearizable : " + result.isLinearizable());
        System.out.println("│  Total time   : " + result.getExecutionTime().toMillis() + " ms");
        System.out.println("│  Operations   : " + result.getStatistics().getTotalOperations());
        System.out.println("└──────────────────────────────────────────────────────");
    }
}