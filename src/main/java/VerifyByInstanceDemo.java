import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.util.concurrent.ConcurrentLinkedQueue;

public class VerifyByInstanceDemo {

    public static void main(String[] args) {
        // Instance is only used to obtain its class.
        ConcurrentLinkedQueue<Integer> q = new ConcurrentLinkedQueue<>();

        VerificationResult result = VerificationFramework
            .verify(q)
            .withThreads(3)
            .withOperations(50)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withSnapshot("gAIsnap")
            .run();

        System.out.println("=== VerifyByInstanceDemo ===");
        System.out.println("Note: verify(Object) uses instance.getClass() only (instance is not executed).");

        System.out.println("Linearizable: " + result.isLinearizable());
        System.out.println("Producer time: " + result.getProdExecutionTime().toMillis() + " ms");
        System.out.println("Verifier time: " + result.getVerifierExecutionTime().toMillis() + " ms");
        System.out.println("Total time: " + result.getExecutionTime().toMillis() + " ms");

    }
}