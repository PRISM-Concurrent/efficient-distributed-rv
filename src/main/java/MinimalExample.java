import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MinimalExample {
  public static void main(String[] args) {
    VerificationResult result = VerificationFramework
        .verify(ConcurrentLinkedQueue.class)
        .withOperations(40)
        .run();

    System.out.println("Linearizable: " + result.isLinearizable());
  }
}