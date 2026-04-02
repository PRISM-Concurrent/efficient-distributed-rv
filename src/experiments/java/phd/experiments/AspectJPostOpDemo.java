package phd.experiments;

import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import phd.distributed.api.A;
import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
import phd.distributed.core.Verifier;
import phd.experiments.aspectj.NativeAspectJSnapshot;
import phd.experiments.aspectj.NativeTraceCollectorPostOp;
import phd.experiments.aspectj.PostOpMonitorAspect;

import java.util.concurrent.TimeUnit;

public class AspectJPostOpDemo {

    private static final Logger LOGGER = LogManager.getLogger(AspectJPostOpDemo.class);
    private static final int OPERATIONS = 40;
    private static final int THREADS = 4;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  POST-OP AspectJ Trace Collection Demo                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.printf("Threads: %d | Operations: %d%n%n", THREADS, OPERATIONS);

        AlgConfig config = new AlgConfig("ConcurrentLinkedQueue", "queue", "offer", "poll");

        runPostOpExperiment(config);
    }

    private static void runPostOpExperiment(AlgConfig config) {
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(config.name);
            if (info == null) return;

            DistAlgorithm algorithm = new A(info.getImplementationClass().getName(), config.methods);

            // 1. Activar el nuevo aspecto y limpiar colector PostOp
            PostOpMonitorAspect.ACTIVE = true;
            NativeTraceCollectorPostOp.clear();

            NativeAspectJSnapshot dummySnap = new NativeAspectJSnapshot();
            Executioner executioner = new Executioner(THREADS, OPERATIONS, algorithm, config.type, dummySnap);

            // 2. Generar Carga
            long prodStart = System.nanoTime();
            executioner.taskProducers(); 
            long prodTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - prodStart);

            PostOpMonitorAspect.ACTIVE = false;

            // 3. Construir Vector desde el colector PostOp
            IPersistentVector xe = NativeTraceCollectorPostOp.buildPersistentVector();

            // 4. Verificar
            long verStart = System.nanoTime();
            Verifier pureVerifier = new Verifier();
            boolean isLinearizable = pureVerifier.verifyDirectTrace(xe, config.type);
            long verTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - verStart);

            // 5. Resultados
            System.out.println("\n=== Post-Op Results ===");
            System.out.println("Linearizable : " + (isLinearizable ? "YES" : "NO"));
            System.out.println("Producers ms : " + prodTimeMs + " ms");
            System.out.println("Verifier ms  : " + verTimeMs + " ms");

            if (xe != null) {
                System.out.println("\n=== X_E Trace (Sample) ===");
                int count = 0;
                for (ISeq s = xe.seq(); s != null && count < 10; s = s.next()) {
                    System.out.println(s.first());
                    count++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private record AlgConfig(String name, String type, String... methods) {}
}