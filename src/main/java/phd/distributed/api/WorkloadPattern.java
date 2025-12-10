package phd.distributed.api;

import phd.distributed.datamodel.MethodInf;
import phd.distributed.datamodel.OperationCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class WorkloadPattern {
    private final int operations;
    private final int threads;       // lo podemos seguir guardando, aunque aquí ya no lo usamos para tid
    private final Random random;
    private final double producerRatio;
    private final PatternType type;

    private enum PatternType {
        UNIFORM, PRODUCER_CONSUMER, READ_HEAVY, WRITE_HEAVY
    }

    private WorkloadPattern(int operations, int threads, long seed,
                            double producerRatio, PatternType type) {
        this.operations = operations;
        this.threads = threads;
        this.random = new Random(seed);
        this.producerRatio = producerRatio;
        this.type = type;
    }

    // ---------- Fábricas de patrones ----------

    public static WorkloadPattern uniform(int operations) {
        return uniform(operations, 4);
    }

    public static WorkloadPattern uniform(int operations, int threads) {
        return new WorkloadPattern(operations, threads, System.currentTimeMillis(),
                0.5, PatternType.UNIFORM);
    }

    public static WorkloadPattern producerConsumer(int operations, int threads, double producerRatio) {
        return new WorkloadPattern(operations, threads, System.currentTimeMillis(),
                producerRatio, PatternType.PRODUCER_CONSUMER);
    }

    public static WorkloadPattern readHeavy(int operations, int threads, double readRatio) {
        // readRatio = probabilidad de lectura
        // ⇒ probabilidad de escritura = 1 - readRatio
        return new WorkloadPattern(operations, threads, System.currentTimeMillis(),
                1.0 - readRatio, PatternType.READ_HEAVY);
    }

    public static WorkloadPattern writeHeavy(int operations, int threads, double writeRatio) {
        // writeRatio = probabilidad de escritura
        return new WorkloadPattern(operations, threads, System.currentTimeMillis(),
                writeRatio, PatternType.WRITE_HEAVY);
    }

    public static WorkloadPattern withSeed(int operations, int threads, long seed) {
        return new WorkloadPattern(operations, threads, seed, 0.5, PatternType.UNIFORM);
    }

    // ======================================================
    //  Generación de OperationCall (sin hilos)
    // ======================================================

    /**
     * Genera una lista de OperationCall de longitud = operations.
     *
     * NO asigna threadId. Eso lo hará el Executioner.
     */
    public List<OperationCall> generateOperations(DistAlgorithm alg, String objectType) {
        List<MethodInf> allMethods = alg.methods();

        // Filtrar métodos de escritura/lectura según el tipo de objeto
        List<MethodInf> writeMethods = allMethods.stream()
                .filter(m -> isWriteMethod(objectType, m.getName()))
                .collect(Collectors.toList());

        List<MethodInf> readMethods = allMethods.stream()
                .filter(m -> isReadMethod(objectType, m.getName()))
                .collect(Collectors.toList());

        List<OperationCall> ops = new ArrayList<>();

        for (int i = 0; i < operations; i++) {
            boolean write = chooseWriteOrRead();
            MethodInf chosenMethod = chooseMethodForKind(write, writeMethods, readMethods, allMethods);

            // Aquí asumimos que ya tienes OperationCall.fromMethod(alg, m, seedIndex)
            // Si no, puedes usar directamente ValueGenerator como hacías en chooseOp.
            OperationCall call = OperationCall.fromMethod(alg, chosenMethod, i);
            ops.add(call);
        }

        return ops;
    }

    // ======================================================
    //  Helpers de selección
    // ======================================================

    /**
     * Decide si la i-ésima operación será "write" o "read",
     * según el PatternType y producerRatio.
     */
    private boolean chooseWriteOrRead() {
        double p = random.nextDouble();
        return switch (type) {
            case PRODUCER_CONSUMER ->
                    // producerRatio = probabilidad de "producer" (enqueue / put / add...)
                    p < producerRatio;
            case READ_HEAVY ->
                    // producerRatio = prob de write (1 - readRatio)
                    p < producerRatio;
            case WRITE_HEAVY ->
                    // producerRatio = prob de write
                    p < producerRatio;
            case UNIFORM ->
                    // 50/50 lectura/escritura
                    p < 0.5;
        };
    }

    /**
     * Elige un MethodInf de acuerdo al tipo (write/read),
     * con fallback si alguna lista está vacía.
     */
    private MethodInf chooseMethodForKind(boolean write,
                                          List<MethodInf> writeMethods,
                                          List<MethodInf> readMethods,
                                          List<MethodInf> allMethods) {
        List<MethodInf> pool;

        if (write && !writeMethods.isEmpty()) {
            pool = writeMethods;
        } else if (!write && !readMethods.isEmpty()) {
            pool = readMethods;
        } else if (!writeMethods.isEmpty()) {
            // fallback: si no hay lecturas, usamos escrituras
            pool = writeMethods;
        } else if (!readMethods.isEmpty()) {
            // fallback: si no hay escrituras, usamos lecturas
            pool = readMethods;
        } else {
            // ultra-fallback: algo raro, usamos cualquier método
            pool = allMethods;
        }

        int idx = random.nextInt(pool.size());
        return pool.get(idx);
    }

    // ======================================================
    //  Clasificación de métodos según objectType
    // ======================================================

    private boolean isWriteMethod(String objectType, String name) {
        return switch (objectType) {
            case "queue" -> switch (name) {
                case "offer", "add", "put" -> true;
                default -> false;
            };
            case "deque" -> switch (name) {
                case "offerFirst", "offerLast", "addFirst", "addLast" -> true;
                default -> false;
            };
            case "set" -> switch (name) {
                case "add", "remove" -> true;
                default -> false;
            };
            case "map" -> switch (name) {
                case "put", "remove" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    private boolean isReadMethod(String objectType, String name) {
        return switch (objectType) {
            case "queue" -> switch (name) {
                case "poll", "peek" -> true;
                default -> false;
            };
            case "deque" -> switch (name) {
                case "pollFirst", "pollLast", "peekFirst", "peekLast" -> true;
                default -> false;
            };
            case "set" -> switch (name) {
                case "contains" -> true;
                default -> false;
            };
            case "map" -> switch (name) {
                case "get", "containsKey", "containsValue" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    // ======================================================
    //  Getters
    // ======================================================

    public int getOperations() {
        return operations;
    }

    public int getThreads() {
        return threads;
    }
}