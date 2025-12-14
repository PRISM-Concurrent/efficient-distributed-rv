import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.util.concurrent.ConcurrentLinkedQueue;

public class VerifyByClassDemo {

    public static void main(String[] args) {
        VerificationResult result = VerificationFramework
            .verify(ConcurrentLinkedQueue.class)
            .withThreads(3)
            .withOperations(50)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withSnapshot("gAIsnap") // or "rawsnap"
            .run();

        System.out.println("=== VerifyByClassDemo ===");
        System.out.println("Linearizable: " + result.isLinearizable());
        System.out.println("Producer time: " + result.getProdExecutionTime().toMillis() + " ms");
        System.out.println("Verifier time: " + result.getVerifierExecutionTime().toMillis() + " ms");
        System.out.println("Total time: " + result.getExecutionTime().toMillis() + " ms");
    }
}