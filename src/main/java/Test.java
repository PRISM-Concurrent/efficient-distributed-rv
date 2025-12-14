import phd.distributed.api.A;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.datamodel.MethodInf;
import phd.distributed.datamodel.OperationCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Test {

    public static void main(String[] args) {

        int THREADS = 3;

        // ============================================================
        // 1. Create a DistAlgorithm with only offer() and poll()
        // ============================================================
        String className = ConcurrentLinkedQueue.class.getName();
        A alg = new A(className, "offer", "poll");

        // ============================================================
        // 2. Obtain MethodInf objects
        // ============================================================
        MethodInf offerMethod = null;
        MethodInf pollMethod  = null;

        for (MethodInf mi : alg.methods()) {
            if (mi.getName().equals("offer")) offerMethod = mi;
            if (mi.getName().equals("poll"))  pollMethod  = mi;
        }

        // ============================================================
        // 3. Build an explicit list of OperationCall (without brackets)
        // ============================================================
        List<OperationCall> schedule = new ArrayList<>();

        schedule.add(new OperationCall(2, offerMethod));   // offer(2)
        schedule.add(new OperationCall(null, pollMethod)); // poll()
        schedule.add(new OperationCall(3, offerMethod));   // offer(3)
        schedule.add(new OperationCall(null, pollMethod)); // poll()
        schedule.add(new OperationCall(5, offerMethod));   // offer(5)

        int OPS = schedule.size();

        // ============================================================
        // 4. Verify using the new VerificationFramework
        // ============================================================
        VerificationResult result = VerificationFramework
            .verify(ConcurrentLinkedQueue.class)
            .withThreads(THREADS)
            .withOperations(OPS)
            .withObjectType("queue")
            .withMethods("offer", "poll")
            .withSchedule(schedule)
            .run();

        System.out.println("Linearizable? " + result.isLinearizable());
        System.out.println("Producer execution time: "
                + result.getProdExecutionTime().toMillis() + " ms");
        System.out.println("Verifier execution time: "
                + result.getVerifierExecutionTime().toMillis() + " ms");
        System.out.println("Total execution time: "
                + result.getExecutionTime().toMillis() + " ms");
    }
}