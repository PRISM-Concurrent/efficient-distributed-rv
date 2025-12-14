
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeoutDemo {

    public static void main(String[] args) {

        try {
            VerificationResult r = VerificationFramework
                .verify(ConcurrentLinkedQueue.class)
                .withThreads(4)
                .withOperations(5000)
                .withObjectType("queue")
                .withMethods("offer", "poll")
                .withTimeout(Duration.ofSeconds(2))
                .run();

            System.out.println("Linearizable: " + r.isLinearizable());
            System.out.println("Producer time: " + r.getProdExecutionTime().toMillis() + " ms");
            System.out.println("Verifier time: " + r.getVerifierExecutionTime().toMillis() + " ms");
            System.out.println("Total time   : " + r.getExecutionTime().toMillis() + " ms");

        } catch (RuntimeException e) {
            System.out.println("=== TimeoutDemo ===");
            System.out.println("Expected timeout caught:");
            System.out.println(e.getMessage());
        }
    }
}