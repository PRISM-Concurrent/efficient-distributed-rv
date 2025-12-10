package phd.distributed.core;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import phd.distributed.api.DistAlgorithm;
import phd.distributed.api.WorkloadPattern;
import phd.distributed.datamodel.OperationCall;
import phd.distributed.snapshot.CollectFAInc;
import phd.distributed.snapshot.CollectRAW;
import phd.distributed.snapshot.Snapshot;

public class Executioner {
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

    public void taskProducers() {
        if (processes <= 0 || this.totalOps <= 0) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(processes);

        int baseOpsPerProc = totalOps / processes;
        int remainder = totalOps % processes;

        for (int pid = 0; pid < processes; pid++) {
            final int processId = pid;
            final int opsForThisProc = baseOpsPerProc + (pid < remainder ? 1 : 0);

            pool.submit(() -> {
                for (int i = 0; i < opsForThisProc; i++) {
                    OperationCall call = OperationCall.chooseOp(A, processId);
                    wrapper.execute(processId, call);
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

        for (int pid = 0; pid < processes; pid++) {
            final int processId      = pid;
            final int opsForThisProc = baseOpsPerProc + (pid < remainder ? 1 : 0);
            final int startIndex     = globalIndex;

            globalIndex += opsForThisProc;

            pool.submit(() -> {
                for (int i = 0; i < opsForThisProc; i++) {
                    int idx = startIndex + i;      // índice global en la lista
                    OperationCall call = ops.get(idx);
                    // aquí el tid lógico es processId, igual que en taskProducers()
                    wrapper.execute(processId, call);
                }
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
