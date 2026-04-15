import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Priority2Example {

    public static void main(String[] args) {
        System.out.println("=== Priority 2 Features Demo (Executioner + JitLin) ===\n");

        // 1. AlgorithmLibrary - Discovery
        System.out.println("1. Algorithm Discovery:");
        AlgorithmLibrary.listAll().forEach(info ->
            System.out.println("  - " + info));

        System.out.println("\n2. Search algorithms (\"queue\"):");
        AlgorithmLibrary.search("queue").forEach(info ->
            System.out.println("  - " + info.getName()));

        // 3. String-based verification: class name → VerificationFramework.verify(String)
        System.out.println("\n3. String-based verification (ConcurrentLinkedQueue):");
        VerificationResult result1 = VerificationFramework
            .verify("java.util.concurrent.ConcurrentLinkedQueue")
            .withThreads(4)
            .withOperations(50)
            .withObjectType("queue")          // para typelin
            .withMethods("offer", "poll")     // métodos a exponer en A
            .run();
        System.out.println("  Linearizable: " + result1.isLinearizable());
        System.out.println("  Time: " + result1.getExecutionTime().toMillis() + " ms");

        // 4. Instance-based verification
        System.out.println("\n4. Instance-based verification (same queue instance):");
        ConcurrentLinkedQueue<Object> instance = new ConcurrentLinkedQueue<>();
        VerificationResult result2 = VerificationFramework
            .verify(instance)
            .withThreads(4)
            .withOperations(50)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .run();
        System.out.println("  Linearizable: " + result2.isLinearizable());
        System.out.println("  Time: " + result2.getExecutionTime().toMillis() + " ms");

        // 5. Producer-Consumer pattern
        System.out.println("\n5. Producer-Consumer pattern (70% producers):");
        WorkloadPattern pcPattern = WorkloadPattern.producerConsumer(100, 4, 0.7);
        VerificationResult result3 = VerificationFramework
            .verify(ConcurrentLinkedQueue.class)
            .withThreads(4)
            .withOperations(100)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withWorkload(pcPattern)
            .run();
        System.out.println("  Linearizable: " + result3.isLinearizable());
        System.out.println("  Time: " + result3.getExecutionTime().toMillis() + " ms");

        // 6. Read-heavy pattern
        System.out.println("\n6. Read-heavy pattern (80% reads):");
        WorkloadPattern readPattern = WorkloadPattern.readHeavy(100, 4, 0.8);
        VerificationResult result4 = VerificationFramework
            .verify(ConcurrentLinkedQueue.class)
            .withThreads(4)
            .withOperations(100)
            .withObjectType("queue")
            // aquí seguirás usando offer/poll
            .withMethods("offer", "poll")
            .withWorkload(readPattern)
            .run();
        System.out.println("  Linearizable: " + result4.isLinearizable());
        System.out.println("  Time: " + result4.getExecutionTime().toMillis() + " ms");

        // 7. Write-heavy pattern
        System.out.println("\n7. Write-heavy pattern (80% writes):");
        WorkloadPattern writePattern = WorkloadPattern.writeHeavy(100, 4, 0.8);
        VerificationResult result5 = VerificationFramework
            .verify(ConcurrentLinkedQueue.class)
            .withThreads(4)
            .withOperations(100)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withWorkload(writePattern)
            .run();
        System.out.println("  Linearizable: " + result5.isLinearizable());
        System.out.println("  Time: " + result5.getExecutionTime().toMillis() + " ms");

        System.out.println("\n=== All Priority 2 features wired to the new framework! ===");
    }
}