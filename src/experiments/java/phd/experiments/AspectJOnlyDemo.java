package phd.experiments;

import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import phd.distributed.api.A;
import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
import phd.distributed.core.Verifier;
import phd.experiments.aspectj.LinearizabilityMonitorAspect;
import phd.experiments.aspectj.NativeAspectJSnapshot;
import phd.experiments.aspectj.NativeTraceCollector;

import java.util.concurrent.TimeUnit;

/**
 * Demo reproducing the experiment by El-Hokayem & Falcone (RV 2018).
 *
 * here there is no guarantee that the captured trace reflects the actual order.
 * The experiment runs N times to estimate the discrepancy rate.
 */
public class AspectJOnlyDemo {

    private static final int OPERATIONS = 40;
    private static final int THREADS    = 4;
    private static final int RUNS       = 100; // to estimate discrepancy rate

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  AspectJ Before/After — El-Hokayem & Falcone Style         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.printf("Threads: %d | Ops: %d | Runs: %d%n%n", THREADS, OPERATIONS, RUNS);

        AlgConfig config = new AlgConfig("ConcurrentLinkedQueue", "queue", "offer", "poll");

        int linearizable    = 0;
        int notLinearizable = 0;
        long totalVerMs     = 0;

        for (int i = 0; i < RUNS; i++) {
            RunResult r = runOnce(config);
            if (r.isLinearizable) linearizable++;
            else notLinearizable++;
            totalVerMs += r.verTimeMs;
        }

        System.out.println("\n=== Summary (" + RUNS + " runs) ===");
        System.out.printf("Linearizable     : %d (%.1f%%)%n", linearizable,    100.0 * linearizable / RUNS);
        System.out.printf("Non-linearizable : %d (%.1f%%)%n", notLinearizable, 100.0 * notLinearizable / RUNS);
        System.out.printf("Average Verifier : %.1f ms%n", (double) totalVerMs / RUNS);
        System.out.println();
        System.out.println("Paper reference (Variant 2, 2 consumers, 10k runs):");
        System.out.println("  → JMOP: 1.28% true | 92.20% false | 6.52% timeout");
        System.out.println("  → Compare with your numbers above.");
    }

    private static RunResult runOnce(AlgConfig config) {
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(config.name);
            DistAlgorithm algorithm = new A(info.getImplementationClass().getName(), config.methods);

            LinearizabilityMonitorAspect.ACTIVE = true;
            NativeTraceCollector.clear();

            NativeAspectJSnapshot dummySnap = new NativeAspectJSnapshot();
            Executioner executioner = new Executioner(THREADS, OPERATIONS, algorithm, config.type, dummySnap);
            executioner.taskProducers();

            LinearizabilityMonitorAspect.ACTIVE = false;

            IPersistentVector xe = NativeTraceCollector.buildPersistentVector();

            long verStart = System.nanoTime();
            Verifier verifier = new Verifier();
            boolean isLinearizable = verifier.verifyDirectTrace(xe, config.type);
            long verTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - verStart);

            return new RunResult(isLinearizable, verTimeMs);

        } catch (Exception e) {
            return new RunResult(false, 0);
        }
    }

    private record RunResult(boolean isLinearizable, long verTimeMs) {}
    private record AlgConfig(String name, String type, String... methods) {}
}