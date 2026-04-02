package phd.experiments;

import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.experiments.aspectj.AspectJSnapshot;

public class BatchExecution {

    private static final int OPERATIONS = 40;
    private static final int THREADS = 4;

    public static void main(String[] args) {

        // Matriz de configuración de algoritmos a probar
        AlgConfig[] configs = {
            new AlgConfig("ConcurrentLinkedQueue", "queue", "offer", "poll"),
           };

        // Guardamos los resultados en una matriz: [algoritmo][estrategia]
        TestResult[][] results = new TestResult[configs.length][3];

        System.out.println("Running batch execution (Threads: " + THREADS + ", Ops: " + OPERATIONS + ") ...");

        for (int i = 0; i < configs.length; i++) {
            AlgConfig config = configs[i];
            System.out.printf("  %s ...%n", config.name);

            // 1. Ejecutar GAIsnap
            results[i][0] = run(config, "gaisnap");

            // 2. Ejecutar RAWsnap
            results[i][1] = run(config, "rawsnap");

            // 3. Ejecutar AspectJ
            results[i][2] = runAspectJ(config);
        }

        printSummary(results, configs);
    }

    // ------------------------------------------------------------
    // Run one algorithm with standard string-based snapshots
    // ------------------------------------------------------------
    private static TestResult run(AlgConfig config, String snapType) {
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(config.name);
            if (info == null) return TestResult.error(config.name, snapType, "Not registered");

            VerificationResult r = VerificationFramework
                .verify(info.getImplementationClass())
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(config.type)
                .withMethods(config.methods)
                .withSnapshot(snapType)
                .run();

            return buildResult(config.name, snapType, r);

        } catch (Exception e) {
            return TestResult.error(config.name, snapType, e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Run one algorithm with AspectJ custom snapshot
    // ------------------------------------------------------------
    private static TestResult runAspectJ(AlgConfig config) {
        String snapType = "aspectj";
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(config.name);
            if (info == null) return TestResult.error(config.name, snapType, "Not registered");

            // Crear el snapshot personalizado que activa el aspecto
            AspectJSnapshot snap = new AspectJSnapshot(THREADS);

            VerificationResult r = VerificationFramework
                .verify(info.getImplementationClass())
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(config.type)
                .withMethods(config.methods)
                .withCustomSnapshot(snap) // <-- Inyección de AspectJ
                .run();

            return buildResult(config.name, snapType, r);

        } catch (Exception e) {
            return TestResult.error(config.name, snapType, e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Helper to build the result object
    // ------------------------------------------------------------
    private static TestResult buildResult(String name, String strategy, VerificationResult r) {
        long prodMs  = r.getProdExecutionTime().toMillis();
        long verMs   = r.getVerifierExecutionTime().toMillis();
        long totalMs = r.getExecutionTime().toMillis();

        double throughput = (totalMs > 0) ? (OPERATIONS * 1000.0) / totalMs : Double.POSITIVE_INFINITY;

        return new TestResult(name, strategy, r.isLinearizable(), prodMs, verMs, totalMs, throughput, null);
    }

    // ------------------------------------------------------------
    // Final summary table
    // ------------------------------------------------------------
    private static void printSummary(TestResult[][] results, AlgConfig[] configs) {
        System.out.println("\n=== Batch Execution Summary ===");
        System.out.println("Threads    : " + THREADS);
        System.out.println("Operations : " + OPERATIONS + "\n");

        System.out.println("┌──────────────────────────┬───────────┬────────────┬────────────┬────────────┬────────────┐");
        System.out.println("│ Algorithm                │ Strategy  │ Producers  │ Verifier   │ Total      │ Throughput │");
        System.out.println("├──────────────────────────┼───────────┼────────────┼────────────┼────────────┼────────────┤");

        for (int i = 0; i < configs.length; i++) {
            for (int j = 0; j < 3; j++) {
                TestResult r = results[i][j];
                
                // Formato para la primera columna: mostrar el nombre solo en la primera fila de la agrupación
                String displayName = (j == 0) ? r.name : "";

                if (!r.success) {
                    System.out.printf("│ %-24s │ %-9s │ %-10s │ %-10s │ %-10s │ %-10s │\n",
                        displayName, r.strategy, "ERROR", "-", "-", "-");
                    continue;
                }

                String linTag = r.linearizable ? "" : "(NON-LIN)";

                System.out.printf("│ %-24s │ %-9s │ %8d ms │ %8d ms │ %8d ms │ %8.0f %s │\n",
                    displayName, r.strategy, r.prodMs, r.verMs, r.totalMs, r.throughput, linTag);
            }
            
            // Separador entre algoritmos para mejor lectura
            if (i < configs.length - 1) {
                System.out.println("├──────────────────────────┼───────────┼────────────┼────────────┼────────────┼────────────┤");
            }
        }
        System.out.println("└──────────────────────────┴───────────┴────────────┴────────────┴────────────┴────────────┘");
    }

    // ------------------------------------------------------------
    // Data structures
    // ------------------------------------------------------------
    private record AlgConfig(String name, String type, String... methods) {}

    private static class TestResult {
        final String name;
        final String strategy;
        final boolean success;
        final boolean linearizable;
        final long prodMs;
        final long verMs;
        final long totalMs;
        final double throughput;
        final String error;

        private TestResult(String name, String strategy, boolean linearizable,
                           long prodMs, long verMs, long totalMs,
                           double throughput, String error) {
            this.name = name;
            this.strategy = strategy;
            this.success = true;
            this.linearizable = linearizable;
            this.prodMs = prodMs;
            this.verMs = verMs;
            this.totalMs = totalMs;
            this.throughput = throughput;
            this.error = error;
        }

        static TestResult error(String name, String strategy, String error) {
            return new TestResult(name, strategy, false, -1, -1, -1, -1, error);
        }
    }
}