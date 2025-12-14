import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

public class VerifyByClassNameDemo {

    public static void main(String[] args) {
        String impl = "java.util.concurrent.ConcurrentLinkedQueue";

        VerificationResult result = VerificationFramework
            .verify(impl)
            .withThreads(3)
            .withOperations(50)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withSnapshot("gAIsnap")
            .run();

        System.out.println("=== VerifyByClassNameDemo ===");
        System.out.println("Class: " + impl);
        System.out.println("Linearizable: " + result.isLinearizable());
        System.out.println("Producer time: " + result.getProdExecutionTime().toMillis() + " ms");
        System.out.println("Verifier time: " + result.getVerifierExecutionTime().toMillis() + " ms");
        System.out.println("Total time: " + result.getExecutionTime().toMillis() + " ms");

    }
}