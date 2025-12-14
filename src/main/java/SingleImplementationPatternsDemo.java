import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.api.WorkloadPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SingleImplementationPatternsDemo {

    // Base parameters
    private static final int THREADS    = 8;
    private static final int OPERATIONS = 20;

    public static void main(String[] args) {

        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  Patterns demo for ConcurrentLinkedQueue          ║");
        System.out.println("║  Threads: " + THREADS + " | Operations: " + OPERATIONS + "                ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        List<Result> results = new ArrayList<>();

        // 1) No explicit workload (random)
        results.add(runScenario(
            "Random",
            null
        ));

        // 2) Producer–consumer
        results.add(runScenario(
            "Producer-Consumer (70% prod)",
            WorkloadPattern.producerConsumer(OPERATIONS, THREADS, 0.7)
        ));

        // 3) Read-heavy
        results.add(runScenario(
            "Read-heavy (80% reads)",
            WorkloadPattern.readHeavy(OPERATIONS, THREADS, 0.8)
        ));

        // 4) Write-heavy
        results.add(runScenario(
            "Write-heavy (80% writes)",
            WorkloadPattern.writeHeavy(OPERATIONS, THREADS, 0.8)
        ));

        waitForLogs();
        printSummary(results);
    }

    // ------------------------------------------------------------
    // Run one scenario
    // ------------------------------------------------------------
    private static Result runScenario(String label, WorkloadPattern pattern) {

        VerificationFramework.VerificationBuilder builder =
            VerificationFramework
                .verify(ConcurrentLinkedQueue.class)
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType("queue")
                .withMethods("offer", "poll")
                .withSnapshot("gAIsnap");

        if (pattern != null) {
            builder = builder.withWorkload(pattern);
        }

        VerificationResult result = builder.run();

        long prodMs = result.getProdExecutionTime().toMillis();
        long verMs  = result.getVerifierExecutionTime().toMillis();
        long total  = result.getExecutionTime().toMillis();

        double throughput =
            (total > 0) ? (OPERATIONS * 1000.0) / total : 0.0;

        return new Result(
            label,
            result.isLinearizable(),
            prodMs,
            verMs,
            total,
            throughput
        );
    }

    // ------------------------------------------------------------
    // Final summary table
    // ------------------------------------------------------------
    private static void printSummary(List<Result> results) {

        System.out.println("\n=== Patterns Comparison Summary ===");
        System.out.println("Threads    : " + THREADS);
        System.out.println("Operations : " + OPERATIONS + "\n");

        System.out.println("┌────────────────────────────┬────────────┬────────────┬────────────┬────────────┐");
        System.out.println("│ Pattern                    │ Producers  │ Verifier   │ Total      │ Throughput │");
        System.out.println("├────────────────────────────┼────────────┼────────────┼────────────┼────────────┤");

        for (Result r : results) {
            System.out.printf(
                "│ %-26s │ %10d ms │ %10d ms │ %10d ms │ %10.0f │\n",
                r.name,
                r.prodMs,
                r.verMs,
                r.totalMs,
                r.throughput
            );
        }

        System.out.println("└────────────────────────────┴────────────┴────────────┴────────────┴────────────┘");

        long ok = results.stream().filter(r -> r.linearizable).count();
        System.out.println("\nSummary:");
        System.out.println("  Linearizable: " + ok + " / " + results.size());
        System.out.println("  Throughput computed as: operations / total_time");
    }

    // ------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------
    private static class Result {
        final String name;
        final boolean linearizable;
        final long prodMs;
        final long verMs;
        final long totalMs;
        final double throughput;

        Result(String name,
               boolean linearizable,
               long prodMs,
               long verMs,
               long totalMs,
               double throughput) {
            this.name = name;
            this.linearizable = linearizable;
            this.prodMs = prodMs;
            this.verMs = verMs;
            this.totalMs = totalMs;
            this.throughput = throughput;
        }
    }

    // ------------------------------------------------------------
    // Small delay for async logs
    // ------------------------------------------------------------
    private static void waitForLogs() {
        try {
            Thread.sleep(200);
            System.out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}