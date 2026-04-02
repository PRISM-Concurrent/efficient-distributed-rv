package phd.experiments;

import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
import phd.distributed.snapshot.Snapshot;
import phd.experiments.aspectj.AspectJSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ProducersBenchmark — isolates the instrumentation cost of each snapshot
 * strategy by measuring ONLY taskProducers() (no linearizability checking).
 *
 * Design:
 *   For each combination of (algorithm, threads, operations, snapType):
 *     1. WARMUP_ROUNDS discarded runs (JVM + Clojure warm-up).
 *     2. MEASURED_ROUNDS timed runs.
 *     3. Report: mean, min, max, and throughput (ops/ms).
 *
 * Two sweep tables are produced:
 *   Table A — fix threads, sweep operations  (shows scalability with load)
 *   Table B — fix operations, sweep threads  (shows concurrency overhead)
 *
 * Run:
 *   # GAIsnap and RAWsnap only:
 *   java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
 *        phd.experiments.ProducersBenchmark
 *
 *   # All three strategies (AspectJ requires -javaagent):
 *   java -javaagent:~/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
 *        -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
 *        phd.experiments.ProducersBenchmark
 *
 * Note on awaitTermination:
 *   Executioner.taskProducers() waits up to 10 s for all threads to finish.
 *   For workloads larger than ~50 000 ops, consider increasing that timeout
 *   in Executioner or reducing OPERATIONS_SWEEP values accordingly.
 */
public class ProducersBenchmark {

    // ── Benchmark parameters ──────────────────────────────────────────────
    static final int WARMUP_ROUNDS   = 5;
    static final int MEASURED_ROUNDS = 10;

    /** Algorithm used for all sweeps (queue is the most exercised in the paper). */
    static final String ALG_CLASS   = "java.util.concurrent.ConcurrentLinkedQueue";
    static final String ALG_TYPE    = "queue";
    static final String[] METHODS   = { "offer", "poll" };

    /** Table A — threads fixed, operations swept. */
    static final int   FIXED_THREADS     = 4;
    static final int[] OPERATIONS_SWEEP  = { 100, 500, 1_000, 5_000, 10_000 };

    /** Table B — operations fixed, threads swept. */
    static final int   FIXED_OPERATIONS  = 1_000;
    static final int[] THREADS_SWEEP     = { 1, 2, 4, 8,
                                             Runtime.getRuntime().availableProcessors() };

    /** Snapshot strategies included in every measurement. */
    enum SnapType { GAIsnap, RAWsnap, AspectJ }

    // ── Result cell ───────────────────────────────────────────────────────
    record Stats(long meanMs, long minMs, long maxMs) {
        /** Operations per millisecond (throughput proxy). */
        double throughput(int ops) {
            return meanMs == 0 ? Double.POSITIVE_INFINITY : (double) ops / meanMs;
        }
        String fmt() {
            if (meanMs < 0) return "  TIMEOUT ";
            return String.format("%5d ms", meanMs);
        }
        String throughputFmt(int ops) {
            if (meanMs < 0) return "   —   ";
            return String.format("%6.1f op/ms", throughput(ops));
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────
    /**
     * Accepts optional arguments:
     *   --format=md|org|tex   Output format (default: md)
     *   --output=<path>       Output file path (default: producers-benchmark.<ext>)
     *
     * Examples:
     *   java ... ProducersBenchmark
     *   java ... ProducersBenchmark --format=org
     *   java ... ProducersBenchmark --format=tex --output=results/table.tex
     */
    public static void main(String[] args) {
        printHeader();

        System.out.println("  Verifying connection to the algorithm...");
        DistAlgorithm probe = buildAlgorithm();
        if (probe == null) { System.out.println("  ERROR: cannot load " + ALG_CLASS); return; }
        System.out.println("  OK.\n");

        // ── Run both tables, collect all results ──────────────────────────
        Stats[][] tableA = runTableA();
        Stats[][] tableB = runTableB();

        printFooter();

        // ── Write report file if --format / --output supplied ─────────────
        ReportWriter.Format fmt = ReportWriter.parseFormat(args);
        String defaultName = "producers-benchmark" + ReportWriter.extension(fmt);
        Path out = ReportWriter.parseOutput(args, defaultName);

        // Write report if --format was explicitly supplied OR --output was given
        boolean writeReport = hasArg(args, "--format") || hasArg(args, "--output");
        if (!writeReport && args.length > 0) writeReport = true; // any arg triggers write

        if (writeReport) {
            writeReport(tableA, tableB, fmt, out);
        }
    }

    private static boolean hasArg(String[] args, String prefix) {
        for (String a : args) if (a.startsWith(prefix)) return true;
        return false;
    }

    // ── Table A: fixed threads, sweep operations ──────────────────────────

    /** Runs Table A and returns results[opIndex][snapIndex: 0=GAI,1=RAW,2=AJ]. */
    static Stats[][] runTableA() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("  Table A — Instrumentation cost vs. number of operations");
        System.out.printf("            Algorithm: %s  |  Threads: %d (fixed)%n", ALG_CLASS, FIXED_THREADS);
        System.out.printf("            Warmup: %d  |  Measured: %d runs per cell%n",
                          WARMUP_ROUNDS, MEASURED_ROUNDS);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("┌──────────┬───────────────────────────────┬───────────────────────────────┬───────────────────────────────┐");
        System.out.println("│  Ops     │ GAIsnap  mean  min   max  tput │ RAWsnap  mean  min   max  tput │ AspectJ  mean  min   max  tput │");
        System.out.println("├──────────┼───────────────────────────────┼───────────────────────────────┼───────────────────────────────┤");

        Stats[][] results = new Stats[OPERATIONS_SWEEP.length][3];
        for (int i = 0; i < OPERATIONS_SWEEP.length; i++) {
            int ops = OPERATIONS_SWEEP[i];
            results[i][0] = measure(FIXED_THREADS, ops, SnapType.GAIsnap);
            results[i][1] = measure(FIXED_THREADS, ops, SnapType.RAWsnap);
            results[i][2] = measure(FIXED_THREADS, ops, SnapType.AspectJ);
            System.out.printf("│ %8d │ %s %s │ %s %s │ %s %s │%n",
                ops,
                cell(results[i][0]), tput(results[i][0], ops),
                cell(results[i][1]), tput(results[i][1], ops),
                cell(results[i][2]), tput(results[i][2], ops));
        }

        System.out.println("└──────────┴───────────────────────────────┴───────────────────────────────┴───────────────────────────────┘");
        System.out.println("  Throughput = ops / mean_ms  (higher is better)");
        System.out.println();
        return results;
    }

    // ── Table B: fixed operations, sweep threads ──────────────────────────

    /** Runs Table B and returns results[threadIndex][snapIndex: 0=GAI,1=RAW,2=AJ]. */
    static Stats[][] runTableB() {
        int[] uniqueThreads = Arrays.stream(THREADS_SWEEP).distinct().sorted().toArray();

        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("  Table B — Instrumentation cost vs. number of threads");
        System.out.printf("            Algorithm: %s  |  Operations: %d (fixed)%n",
                          ALG_CLASS, FIXED_OPERATIONS);
        System.out.printf("            Warmup: %d  |  Measured: %d runs per cell%n",
                          WARMUP_ROUNDS, MEASURED_ROUNDS);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("┌──────────┬───────────────────────────────┬───────────────────────────────┬───────────────────────────────┐");
        System.out.println("│  Threads │ GAIsnap  mean  min   max  tput │ RAWsnap  mean  min   max  tput │ AspectJ  mean  min   max  tput │");
        System.out.println("├──────────┼───────────────────────────────┼───────────────────────────────┼───────────────────────────────┤");

        Stats[][] results = new Stats[uniqueThreads.length][3];
        for (int i = 0; i < uniqueThreads.length; i++) {
            int t = uniqueThreads[i];
            results[i][0] = measure(t, FIXED_OPERATIONS, SnapType.GAIsnap);
            results[i][1] = measure(t, FIXED_OPERATIONS, SnapType.RAWsnap);
            results[i][2] = measure(t, FIXED_OPERATIONS, SnapType.AspectJ);
            System.out.printf("│ %8d │ %s %s │ %s %s │ %s %s │%n",
                t,
                cell(results[i][0]), tput(results[i][0], FIXED_OPERATIONS),
                cell(results[i][1]), tput(results[i][1], FIXED_OPERATIONS),
                cell(results[i][2]), tput(results[i][2], FIXED_OPERATIONS));
        }

        System.out.println("└──────────┴───────────────────────────────┴───────────────────────────────┴───────────────────────────────┘");
        System.out.println("  tput = throughput (ops / mean_ms)");
        System.out.println();
        return results;
    }

    // ── Core measurement ──────────────────────────────────────────────────

    /**
     * Measures taskProducers() for a given (threads, ops, snapType) combination.
     *
     * Returns a Stats with mean / min / max across MEASURED_ROUNDS runs,
     * or Stats(-1, -1, -1) if every run timed out or threw an exception.
     */
    static Stats measure(int threads, int ops, SnapType type) {
        // warm-up: discard results, let JVM and Clojure reach steady state
        for (int w = 0; w < WARMUP_ROUNDS; w++) {
            runOnce(threads, ops, type);
        }

        long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        int good = 0;

        for (int m = 0; m < MEASURED_ROUNDS; m++) {
            long ms = runOnce(threads, ops, type);
            if (ms >= 0) {
                sum += ms;
                if (ms < min) min = ms;
                if (ms > max) max = ms;
                good++;
            }
        }

        if (good == 0) return new Stats(-1, -1, -1);
        return new Stats(sum / good, min, max);
    }

    /**
     * Executes one run of taskProducers() only, returning elapsed millis.
     * Returns -1 on exception or timeout.
     *
     * Key design note:
     *   We build a FRESH Executioner (and Snapshot) for each run so that
     *   internal state (Clojure atoms, atomic counters) does not accumulate
     *   between runs and distort measurements.
     */
    static long runOnce(int threads, int ops, SnapType type) {
        try {
            DistAlgorithm algorithm = buildAlgorithm();
            if (algorithm == null) return -1;

            Executioner exec = buildExecutioner(threads, ops, type, algorithm);

            long start = System.nanoTime();
            exec.taskProducers();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        } catch (Exception e) {
            return -1;
        }
    }

    // ── Factory helpers ───────────────────────────────────────────────────

    static DistAlgorithm buildAlgorithm() {
        try {
            return new A(ALG_CLASS, METHODS);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds an Executioner wired to the correct Snapshot for each strategy.
     *
     * GAIsnap → CollectFAInc   (created via Executioner string constructor)
     * RAWsnap → CollectRAW     (created via Executioner string constructor)
     * AspectJ → AspectJSnapshot (injected via the Snapshot-accepting constructor
     *                            added in this paper's refactoring)
     *
     * For AspectJ, creating a new AspectJSnapshot also resets the logrAw
     * Clojure state (init-logs!) and registers the instance as the active
     * target for LinearizabilityMonitorAspect.
     */
    static Executioner buildExecutioner(int threads, int ops,
                                        SnapType type, DistAlgorithm alg) {
        return switch (type) {
            case GAIsnap -> new Executioner(threads, ops, alg, ALG_TYPE, "gAIsnap");
            case RAWsnap -> new Executioner(threads, ops, alg, ALG_TYPE, "rawsnap");
            case AspectJ -> {
                AspectJSnapshot snap = new AspectJSnapshot(threads);
                yield new Executioner(threads, ops, alg, ALG_TYPE, snap);
            }
        };
    }

    // ── Formatting helpers ────────────────────────────────────────────────

    /** Formats a Stats cell as "mean / min / max". */
    static String cell(Stats s) {
        if (s == null || s.meanMs() < 0)
            return " TIMEOUT  ";
        return String.format("%4d/%3d/%3d ms", s.meanMs(), s.minMs(), s.maxMs());
    }

    /** Formats throughput, or "N/A" on error. */
    static String tput(Stats s, int ops) {
        if (s == null || s.meanMs() < 0) return " N/A   ";
        double t = s.throughput(ops);
        if (Double.isInfinite(t)) return " ∞ op/ms";
        return String.format("%6.1f o/ms", t);
    }

    // ── Report file ───────────────────────────────────────────────────────

    static void writeReport(Stats[][] tableA, Stats[][] tableB,
                            ReportWriter.Format fmt, Path path) {
        try (ReportWriter w = new ReportWriter(path, fmt)) {

            w.title("ProducersBenchmark — Instrumentation Overhead");
            w.metadata("Algorithm",   ALG_CLASS);
            w.metadata("Warmup runs", String.valueOf(WARMUP_ROUNDS));
            w.metadata("Measured runs", String.valueOf(MEASURED_ROUNDS));
            w.metadata("Machine",
                System.getProperty("os.name") + " / " +
                Runtime.getRuntime().availableProcessors() + " CPUs");
            w.metadata("JVM",
                System.getProperty("java.vm.name") + " " +
                System.getProperty("java.version"));
            w.blank();

            // ── Table A ───────────────────────────────────────────────────
            w.section("Table A — Operations sweep (threads = " + FIXED_THREADS + ")");
            w.text("Only taskProducers() is measured. Throughput in ops/ms (higher = better).");

            List<String> colsA = List.of(
                "Ops", "GAI mean (ms)", "GAI tput", "RAW mean (ms)", "RAW tput",
                "AJ mean (ms)", "AJ tput");

            List<List<String>> rowsA = new java.util.ArrayList<>();
            for (int i = 0; i < OPERATIONS_SWEEP.length; i++) {
                int ops = OPERATIONS_SWEEP[i];
                rowsA.add(List.of(
                    String.valueOf(ops),
                    meanStr(tableA[i][0]), tputStr(tableA[i][0], ops),
                    meanStr(tableA[i][1]), tputStr(tableA[i][1], ops),
                    meanStr(tableA[i][2]), tputStr(tableA[i][2], ops)
                ));
            }
            w.table("Instrumentation cost vs number of operations (threads=" + FIXED_THREADS + ")",
                    colsA, rowsA, 0, 1, 2, 3, 4, 5, 6);

            // ── Table B ───────────────────────────────────────────────────
            int[] uniqueThreads =
                Arrays.stream(THREADS_SWEEP).distinct().sorted().toArray();
            w.section("Table B — Threads sweep (operations = " + FIXED_OPERATIONS + ")");
            w.text("Only taskProducers() is measured. Throughput in ops/ms (higher = better).");

            List<String> colsB = List.of(
                "Threads", "GAI mean (ms)", "GAI tput", "RAW mean (ms)", "RAW tput",
                "AJ mean (ms)", "AJ tput");

            List<List<String>> rowsB = new java.util.ArrayList<>();
            for (int i = 0; i < uniqueThreads.length; i++) {
                rowsB.add(List.of(
                    String.valueOf(uniqueThreads[i]),
                    meanStr(tableB[i][0]), tputStr(tableB[i][0], FIXED_OPERATIONS),
                    meanStr(tableB[i][1]), tputStr(tableB[i][1], FIXED_OPERATIONS),
                    meanStr(tableB[i][2]), tputStr(tableB[i][2], FIXED_OPERATIONS)
                ));
            }
            w.table("Instrumentation cost vs number of threads (ops=" + FIXED_OPERATIONS + ")",
                    colsB, rowsB, 0, 1, 2, 3, 4, 5, 6);

        } catch (IOException e) {
            System.err.println("  ERROR writing report: " + e.getMessage());
            return;
        }
        System.out.println("  Report written to: " + path.toAbsolutePath());
    }

    // Helpers for report strings
    static String meanStr(Stats s) {
        return (s == null || s.meanMs() < 0) ? "N/A" : String.valueOf(s.meanMs());
    }
    static String tputStr(Stats s, int ops) {
        if (s == null || s.meanMs() <= 0) return "N/A";
        return String.format("%.1f", s.throughput(ops));
    }

    // ── Header / footer ───────────────────────────────────────────────────

    static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  ProducersBenchmark — instrumentation cost, no linearizability check  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.printf("  Measuring only taskProducers().  Each cell: warmup %d, measured %d.%n%n",
            WARMUP_ROUNDS, MEASURED_ROUNDS);
    }

    static void printFooter() {
        System.out.println("  Benchmark complete.");
        System.out.printf("  Machine : %s%n", System.getProperty("os.name"));
        System.out.printf("  CPUs    : %d logical%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("  JVM     : %s %s%n",
            System.getProperty("java.vm.name"),
            System.getProperty("java.version"));
        System.out.printf("  Heap    : %.0f MB used / %.0f MB max%n",
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1e6,
            Runtime.getRuntime().maxMemory() / 1e6);
    }
}
