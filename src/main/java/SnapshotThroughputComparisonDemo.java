import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.api.WorkloadPattern;
import phd.distributed.core.Executioner;
import phd.distributed.datamodel.OperationCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SnapshotThroughputComparisonDemo {

    private static final int THREADS = 4;
    private static final int OPS = 5000;
    private static final int RUNS = 5;

    public static void main(String[] args) {

        System.out.println("=== Snapshot Throughput Comparison (Producers Only) ===");
        System.out.println("Threads      : " + THREADS);
        System.out.println("Operations   : " + OPS);
        System.out.println("Runs/snapshot: " + RUNS);
        System.out.println("Verification : DISABLED\n");

        // 1) Algorithm wrapper (spec-safe methods only)
        DistAlgorithm alg =
            new A(ConcurrentLinkedQueue.class.getName(), "offer", "poll");

        // 2) Deterministic workload (same ops for all runs)
        WorkloadPattern pattern =
            WorkloadPattern.withSeed(OPS, THREADS, 123456789L);
        List<OperationCall> schedule =
            pattern.generateOperations(alg, "queue");

        // 3) Warm-up
        warmup(alg, schedule);

        // 4) Run benchmarks
        List<Result> gaiResults = runMany("gAIsnap", alg, schedule);
        List<Result> rawResults = runMany("rawsnap", alg, schedule);

        waitForLogs();

        // 5) Print summary
        printSummary(gaiResults, rawResults);
    }

    // ------------------------------------------------------------
    // Warm-up
    // ------------------------------------------------------------
    private static void warmup(DistAlgorithm alg, List<OperationCall> schedule) {
        int warmOps = Math.min(300, schedule.size());
        List<OperationCall> warmSchedule = schedule.subList(0, warmOps);

        System.out.println("Warm-up (" + warmOps + " ops)...");
        Executioner warm =
            new Executioner(THREADS, warmOps, alg, "queue", "gAIsnap");
        warm.taskProducersSeed(warmSchedule);
        System.out.println("Warm-up done.\n");
    }

    // ------------------------------------------------------------
    // Multiple runs
    // ------------------------------------------------------------
    private static List<Result> runMany(String snapType,
                                        DistAlgorithm alg,
                                        List<OperationCall> schedule) {

        System.out.println("=== " + snapType + " ===");

        List<Result> results = new ArrayList<>();

        for (int i = 1; i <= RUNS; i++) {
            Result r = runOnce(snapType, alg, schedule, i);
            results.add(r);
        }

        System.out.println();
        return results;
    }

    // ------------------------------------------------------------
    // Single run
    // ------------------------------------------------------------
    private static Result runOnce(String snapType,
                                  DistAlgorithm alg,
                                  List<OperationCall> schedule,
                                  int runId) {

        Executioner exec =
            new Executioner(THREADS, OPS, alg, "queue", snapType);

        long start = System.nanoTime();
        exec.taskProducersSeed(schedule);
        long end = System.nanoTime();

        long nanos = end - start;
        double seconds = nanos / 1_000_000_000.0;
        double timeMs = seconds * 1000.0;
        double throughput =
            (seconds > 0) ? OPS / seconds : Double.POSITIVE_INFINITY;

        System.out.printf(
            "Run %d → time: %.2f ms | throughput: %.0f ops/sec%n",
            runId, timeMs, throughput
        );

        return new Result(timeMs, throughput);
    }

    // ------------------------------------------------------------
    // Final summary
    // ------------------------------------------------------------
    private static void printSummary(List<Result> gai,
                                     List<Result> raw) {

        Stats g = Stats.from(gai);
        Stats r = Stats.from(raw);

        System.out.println("=== Final Comparison (averaged over " + RUNS + " runs) ===");
        System.out.println("┌───────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("│ Snapshot  │ Avg time ms  │ Std dev ms   │ Throughput   │");
        System.out.println("├───────────┼──────────────┼──────────────┼──────────────┤");

        System.out.printf(
            "│ %-9s │ %-12.2f │ %-12.2f │ %-12.0f │%n",
            "gAIsnap", g.avgTimeMs, g.stdTimeMs, g.avgThroughput
        );
        System.out.printf(
            "│ %-9s │ %-12.2f │ %-12.2f │ %-12.0f │%n",
            "rawsnap", r.avgTimeMs, r.stdTimeMs, r.avgThroughput
        );

        System.out.println("└───────────┴──────────────┴──────────────┴──────────────┘");

        double speedup = g.avgThroughput / r.avgThroughput;

        System.out.println("\nRelative speed:");
        System.out.println("  GAI / RAW throughput = " +
            String.format("%.2f×", speedup));

        if (speedup > 1.0) {
            System.out.println("  → GAI is faster on average");
        } else if (speedup < 1.0) {
            System.out.println("  → RAW is faster on average");
        } else {
            System.out.println("  → Same performance");
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private static class Result {
        final double timeMs;
        final double throughput;

        Result(double timeMs, double throughput) {
            this.timeMs = timeMs;
            this.throughput = throughput;
        }
    }

    private static class Stats {
        final double avgTimeMs;
        final double stdTimeMs;
        final double avgThroughput;

        private Stats(double avgTimeMs,
                      double stdTimeMs,
                      double avgThroughput) {
            this.avgTimeMs = avgTimeMs;
            this.stdTimeMs = stdTimeMs;
            this.avgThroughput = avgThroughput;
        }

        static Stats from(List<Result> rs) {
            double sumTime = 0, sumThr = 0;
            for (Result r : rs) {
                sumTime += r.timeMs;
                sumThr  += r.throughput;
            }
            double avgTime = sumTime / rs.size();
            double avgThr  = sumThr  / rs.size();

            double var = 0;
            for (Result r : rs) {
                double d = r.timeMs - avgTime;
                var += d * d;
            }
            double std = Math.sqrt(var / rs.size());

            return new Stats(avgTime, std, avgThr);
        }
    }

    private static void waitForLogs() {
        try {
            Thread.sleep(300);
            System.out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}