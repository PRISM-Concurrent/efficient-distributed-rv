package phd.distributed.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import clojure.lang.IPersistentVector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import phd.distributed.api.DistAlgorithm;
import phd.distributed.api.WorkloadPattern;
import phd.distributed.datamodel.OperationCall;
import phd.distributed.snapshot.CollectFAInc;
import phd.distributed.snapshot.CollectRAW;
import phd.distributed.snapshot.Snapshot;

public class Executioner {
    private static final Logger LOGGER = LogManager.getLogger(Executioner.class);
    final int processes;
    final int totalOps;
    private String objectType;
    private final Snapshot c;
    private final DistAlgorithm A;
    private final Verifier verifier;
    private final Wrapper wrapper;
    private volatile long verifierNanos = -1L;

 // ========= Helper para elegir snapshot según snapType =========
    private static Snapshot createSnapshot(String snapType, int processes) {
        if (snapType == null) {
            // default
            return new CollectFAInc(processes);
        }
        String s = snapType.trim().toLowerCase();
        switch (s) {
            case "gaisnap":
                return new CollectFAInc(processes);
            case "rawsnap":
                return new CollectRAW(processes);
            default:
                // fallback razonable: GAIsnap
                return new CollectFAInc(processes);
        }
    }

    // usa GAIsnap y queue por default
    public Executioner(int processes, int op, DistAlgorithm A) {
        this(processes, op, A, "queue", "gAIsnap");
    }

    // usando GAIsnap por default
    public Executioner(int processes, int op, DistAlgorithm A, String objectType) {
        this(processes, op, A, objectType, "gAIsnap");
    }

    // ======= Nuevo constructor completo: recibe objectType y snapType =======
    public Executioner(int processes, int op, DistAlgorithm A,
                       String objectType, String snapType) {
        this.processes = processes;
        this.totalOps  = op;
        this.A         = A;
        this.objectType = objectType;

        // elegir implementación de snapshot según snapType
        this.c = createSnapshot(snapType, processes);

        this.wrapper  = new Wrapper(A, c);
        this.verifier = new Verifier(c);
    }

    public Executioner(int processes, int op, DistAlgorithm A, String objectType, Snapshot snapshot) {
        this.processes  = processes;
        this.totalOps   = op;
        this.A          = A;
        this.objectType = objectType;
        this.c          = snapshot;
        this.wrapper    = new Wrapper(A, c);
        this.verifier   = new Verifier(c);
    }

 


    /** Default upper bound for waiting on the producer pool to finish. */
    private static final long DEFAULT_POOL_TIMEOUT_SECONDS = 30;

    /**
     * Gracefully shut down the pool, awaiting termination and forcing
     * interruption if the timeout is exceeded. Silent truncation of the
     * run would invalidate benchmark measurements, so we log a warning
     * and call {@code shutdownNow()} on timeout.
     */
    private static void shutdownAndAwait(ExecutorService pool, long timeout, TimeUnit unit) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout, unit)) {
                LOGGER.warn("Producer pool did not terminate within {} {}; forcing shutdown. "
                          + "Benchmark measurements for this run may be incomplete.",
                          timeout, unit.name().toLowerCase());
                pool.shutdownNow();
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOGGER.error("Producer pool did not terminate after shutdownNow().");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void taskProducers() {
        if (processes <= 0 || this.totalOps <= 0) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(processes);

        int baseOpsPerProc = totalOps / processes;
        int remainder = totalOps % processes;

        List<Future<?>> futures = new ArrayList<>();
        for (int pid = 0; pid < processes; pid++) {
            final int processId = pid;
            final int opsForThisProc = baseOpsPerProc + (pid < remainder ? 1 : 0);

            futures.add(pool.submit(() -> {
                for (int i = 0; i < opsForThisProc; i++) {
                    OperationCall call = OperationCall.chooseOp(A, processId);
                    wrapper.execute(processId, call);
                }
            }));
        }
        shutdownAndAwait(pool, DEFAULT_POOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // Surface any exceptions that were silently swallowed by pool.submit()
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get();
            } catch (ExecutionException e) {
                LOGGER.error("Producer thread {} failed: {}", i, e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void taskProducersSeed(List<OperationCall> ops) {
        if (processes <= 0 || this.totalOps <= 0 || ops == null || ops.isEmpty()) {
            return;
        }

        if (ops.size() < totalOps) {
            throw new IllegalArgumentException(
                "Workload provided " + ops.size() +
                " operations, but Executioner requires " + totalOps
            );
        }

        ExecutorService pool = Executors.newFixedThreadPool(processes);

        int baseOpsPerProc = totalOps / processes;
        int remainder      = totalOps % processes;

        int globalIndex = 0; // índice en la lista ops
        List<Future<?>> futures = new ArrayList<>();

        for (int pid = 0; pid < processes; pid++) {
            final int processId      = pid;
            final int opsForThisProc = baseOpsPerProc + (pid < remainder ? 1 : 0);
            final int startIndex     = globalIndex;

            globalIndex += opsForThisProc;

            futures.add(pool.submit(() -> {
                for (int i = 0; i < opsForThisProc; i++) {
                    int idx = startIndex + i;      // índice global en la lista
                    OperationCall call = ops.get(idx);
                    // aquí el tid lógico es processId, igual que en taskProducers()
                    wrapper.execute(processId, call);
                }
            }));
        }

        shutdownAndAwait(pool, DEFAULT_POOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get();
            } catch (ExecutionException e) {
                LOGGER.error("Producer thread {} failed: {}", i, e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean taskVerifiers() {
        long start = System.nanoTime();
        boolean ok;
        try {
            ok = verifier.checkLinearizabilityJitLin(this.objectType);
        } finally {
            this.verifierNanos = System.nanoTime() - start;
        }
        return ok;
    }

    public long getVerifierTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(verifierNanos);
    }

    
    public IPersistentVector getTrace() {
        return this.c.buildXE();
    }
}
