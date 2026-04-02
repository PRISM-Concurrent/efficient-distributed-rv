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
import phd.experiments.aspectj.LinearizabilityMonitorAspect;
import phd.experiments.aspectj.NativeAspectJSnapshot;
import phd.experiments.aspectj.NativeTraceCollector;

import java.util.concurrent.TimeUnit;

public class AspectJOnlyDemo {

    private static final Logger LOGGER = LogManager.getLogger(AspectJOnlyDemo.class);
    private static final int OPERATIONS = 40;
    private static final int THREADS = 4;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  Native AspectJ Trace Collection Demo                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.printf("Threads: %d | Operations: %d%n%n", THREADS, OPERATIONS);

        AlgConfig config = new AlgConfig("ConcurrentLinkedQueue", "queue", "offer", "poll");

        System.out.println("Running " + config.name + " with Native AspectJ ...");
        runNativeAspectJAndPrintTrace(config);
    }

    private static void runNativeAspectJAndPrintTrace(AlgConfig config) {
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(config.name);
            if (info == null) {
                System.err.println("Error: Algorithm " + config.name + " not registered.");
                return;
            }

            DistAlgorithm algorithm = new A(info.getImplementationClass().getName(), config.methods);

            // 1. Preparar AspectJ y el Recolector Nativo
            LinearizabilityMonitorAspect.ACTIVE = true;
            NativeTraceCollector.clear();

            // 2. GENERACIÓN DE CARGA (Producers)
            // Usamos NativeAspectJSnapshot como un "dummy" para que Executioner funcione felizmente.
            // La recolección real la hace el aspecto silenciosamente.
            NativeAspectJSnapshot dummySnap = new NativeAspectJSnapshot();
            Executioner executioner = new Executioner(THREADS, OPERATIONS, algorithm, config.type, dummySnap);

            long prodStart = System.nanoTime();
            
            // ¡NO BORRES ESTA LÍNEA! Aquí es donde corren los hilos y AspectJ recolecta la magia.
            executioner.taskProducers(); 
            
            long prodTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - prodStart);

            // Apagamos el interceptor
            LinearizabilityMonitorAspect.ACTIVE = false;

            // 3. CONSTRUCCIÓN DEL VECTOR (De Java a Clojure)
            IPersistentVector xe = NativeTraceCollector.buildPersistentVector();

            // 4. VERIFICACIÓN DESACOPLADA
            // Usamos tu nueva instancia limpia de Verifier sin pasar por el Wrapper
            long verStart = System.nanoTime();
            
            Verifier pureVerifier = new Verifier();
            boolean isLinearizable = pureVerifier.verifyDirectTrace(xe, config.type);
            
            long verTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - verStart);

            // 5. Imprimir resultados
            System.out.println("\n=== Verification Summary ===");
            System.out.println("Linearizable : " + (isLinearizable ? "YES" : "NO"));
            System.out.println("Producers ms : " + prodTimeMs + " ms");
            System.out.println("Verifier ms  : " + verTimeMs + " ms");

            // 6. Imprimir la traza recolectada
            System.out.println("\n=== Execution Trace (X_E) ===");
            
            if (xe == null || xe.count() == 0) {
                System.out.println("⚠️ Warning: Trace is empty! Check your pointcut or ajc compiler.");
            } else {
                System.out.println("Raw X_E Vector: " + xe.toString());
                System.out.println("--------------------------------------------------");
                int step = 1;
                for (ISeq s = xe.seq(); s != null; s = s.next()) {
                    System.out.printf("%3d: %s%n", step++, s.first());
                }
            }

        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private record AlgConfig(String name, String type, String... methods) {}
}