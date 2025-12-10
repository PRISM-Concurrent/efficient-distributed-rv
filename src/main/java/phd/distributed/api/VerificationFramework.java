package phd.distributed.api;

import phd.distributed.core.Executioner;
import phd.distributed.datamodel.OperationCall;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class VerificationFramework {

    public static VerificationBuilder verify(Class<?> algorithmClass) {
        return new VerificationBuilder(algorithmClass);
    }

    public static VerificationBuilder verify(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return new VerificationBuilder(clazz);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }

    public static VerificationBuilder verify(Object instance) {
        return new VerificationBuilder(instance.getClass());
    }

    // ============================================================
    //  Builder
    // ============================================================
    public static class VerificationBuilder {
        private final Class<?> algorithmClass;

        private int threads      = 8;
        private String snapType  = "gAIsnap"; // o "rAwsnap"
        private int operations   = 1000;
        private Duration timeout = Duration.ofMinutes(5);
        private Long seed        = null;      // por si luego quieres controlar el WorkloadPattern
        private WorkloadPattern workload = null;

        // schedule fija de OperationCall (sin tids)
        private List<OperationCall> fixedSchedule = null;

        // Tipo lógico de estructura para typelin: "queue", "map", "set", "deque", ...
        private String objectType = "queue";

        // Subconjunto de métodos que expondrá A (offer/poll, put/get/remove, etc.)
        private String[] methods = null;

        private VerificationBuilder(Class<?> algorithmClass) {
            this.algorithmClass = algorithmClass;
        }

        public VerificationBuilder withThreads(int threads) {
            this.threads = threads;
            return this;
        }

        public VerificationBuilder withOperations(int operations) {
            this.operations = operations;
            return this;
        }

        public VerificationBuilder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public VerificationBuilder withSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public VerificationBuilder withWorkload(WorkloadPattern pattern) {
            this.workload = pattern;
            return this;
        }

        /** Tipo lógico para la especificación secuencial: "queue", "map", "set", "deque"... */
        public VerificationBuilder withObjectType(String objectType) {
            this.objectType = objectType;
            return this;
        }

        /** Nombres de métodos a incluir desde la clase Java (offer/poll, put/get/remove...) */
        public VerificationBuilder withMethods(String... methods) {
            this.methods = methods;
            return this;
        }

        /** Schedule fija de OperationCall (sin hilos asignados; Executioner reparte por processId). */
        public VerificationBuilder withSchedule(List<OperationCall> schedule) {
            this.fixedSchedule = schedule;
            return this;
        }

        /** Tipo de snapshot: "gAIsnap" o "rAwsnap" (CollectFAInc / CollectRAW). */
        public VerificationBuilder withSnapshot(String snapType) {
            this.snapType = snapType;
            return this;
        }

        // ========================================================
        //  Synchronous execution
        // ========================================================
        public VerificationResult run() {
            try {
                return runAsync().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Verification interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Verification failed", e.getCause());
            } catch (TimeoutException e) {
                throw new RuntimeException("Verification timeout after " + timeout, e);
            }
        }

        // ========================================================
        //  Async execution - usando Executioner + JitLin
        // ========================================================
        public CompletableFuture<VerificationResult> runAsync() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 1) Construir DistAlgorithm usando tu wrapper A
                    String implClassName = algorithmClass.getName();
                    DistAlgorithm algorithm =
                        (methods == null || methods.length == 0)
                            ? new A(implClassName)
                            : new A(implClassName, methods);

                    // 2) Crear Executioner (usa snapshot según snapType + JitLin)
                    Executioner executioner =
                        new Executioner(threads, operations, algorithm, objectType, snapType);

                    // 3) FASE PRODUCTORES
                    long producersStart = System.nanoTime();

                    if (fixedSchedule != null) {
                        // usamos exactamente la lista de OperationCall preconstruida
                        executioner.taskProducersSeed(fixedSchedule);

                    } else if (workload != null) {
                        // workload → lista de OperationCall (sin tids)
                        List<OperationCall> ops =
                            workload.generateOperations(algorithm, objectType);
                        executioner.taskProducersSeed(ops);

                    } else {
                        // Modo aleatorio: OperationCall.chooseOp(...)
                        executioner.taskProducers();
                    }

                    long producersEnd = System.nanoTime();
                    Duration producersTime =
                        Duration.ofNanos(producersEnd - producersStart);

                    // 4) FASE VERIFICACIÓN (JitLin)
                    long verifierStart = System.nanoTime();
                    boolean correct = executioner.taskVerifiers();
                    long verifierEnd = System.nanoTime();
                    Duration verifierTime =
                        Duration.ofNanos(verifierEnd - verifierStart);

                    Duration totalTime = producersTime.plus(verifierTime);

                    VerificationResult.ExecutionStatistics stats =
                        new VerificationResult.ExecutionStatistics(
                            operations,
                            0L  // si luego lees el tamaño de X_E puedes poner aquí los eventos procesados
                        );

                    System.out.println("  ↳ Producer phase time : " + producersTime.toMillis() + " ms");
                    System.out.println("  ↳ Verifier phase time : " + verifierTime.toMillis() + " ms");
                    System.out.println("  ↳ Total verification   : " + totalTime.toMillis() + " ms");

                    return new VerificationResult(correct, totalTime, null, stats);

                } catch (Exception e) {
                    throw new RuntimeException("Verification failed", e);
                }
            });
        }
    }
}