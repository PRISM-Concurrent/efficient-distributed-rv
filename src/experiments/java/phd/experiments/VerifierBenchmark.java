package phd.experiments;

import clojure.lang.IPersistentVector;
import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
import phd.distributed.core.Verifier;
import phd.experiments.aspectj.LinearizabilityMonitorAspect;
import phd.experiments.aspectj.NativeAspectJSnapshot;
import phd.experiments.aspectj.NativeTraceCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VerifierBenchmark {

    // ── Parámetros Georges et al. ─────────────────────────────────────────
    static final int WARMUP_ROUNDS   = 5;
    static final int MEASURED_ROUNDS = 10;

    // ── Parámetros Table C ────────────────────────────────────────────────
    static final int[] OPERATIONS_SWEEP = {
        100, 500, 1_000, 5_000, 10_000, 50_000, 100_000
    };
    static final int[] THREADS_SWEEP_C = { 2, 4, 8, 16, 32, 64 };

    // ── Parámetros Table D ────────────────────────────────────────────────
    static final int   VERDICT_OPS     = 50;
    static final int   VERDICT_RUNS    = 100;
    static final int[] THREADS_VERDICT = { 2, 4, 8, 16, 32, 64 };

    // ── Parámetros Table E ────────────────────────────────────────────────
    static final int TABLE_E_OPS     = 30;
    static final int TABLE_E_RUNS    = 10;
    static final int TABLE_E_THREADS = 4;

    // ── Algoritmo principal ───────────────────────────────────────────────
    static final String   ALG_CLASS = "java.util.concurrent.ConcurrentLinkedQueue";
    static final String   ALG_TYPE  = "queue";
    static final String[] METHODS   = { "offer", "poll" };

    // ── Algoritmos para Table E ───────────────────────────────────────────
    record AlgConfig(String name, String clazz, String type,
                     boolean expectedLinearizable, String... methods) {}

    static final AlgConfig[] TABLE_E_ALGORITHMS = {
        // Implementaciones correctas
        new AlgConfig("ConcurrentLinkedQueue",  "java.util.concurrent.ConcurrentLinkedQueue",
                      "queue",  true,  "offer", "poll"),
        new AlgConfig("LinkedBlockingQueue",    "java.util.concurrent.LinkedBlockingQueue",
                      "queue",  true,  "offer", "poll"),
        new AlgConfig("LinkedTransferQueue",    "java.util.concurrent.LinkedTransferQueue",
                      "queue",  true,  "offer", "poll"),
        new AlgConfig("ConcurrentLinkedDeque",  "java.util.concurrent.ConcurrentLinkedDeque",
                      "deque",  true,  "offerFirst", "offerLast", "pollFirst", "pollLast"),
        new AlgConfig("LinkedBlockingDeque",    "java.util.concurrent.LinkedBlockingDeque",
                      "deque",  true,  "offerFirst", "offerLast", "pollFirst", "pollLast"),
        new AlgConfig("ConcurrentSkipListSet",  "java.util.concurrent.ConcurrentSkipListSet",
                      "set",    true,  "add", "remove", "contains"),
        new AlgConfig("ConcurrentHashMap",      "java.util.concurrent.ConcurrentHashMap",
                      "map",    true,  "put", "get", "remove"),
        new AlgConfig("ConcurrentSkipListMap",  "java.util.concurrent.ConcurrentSkipListMap",
                      "map",    true,  "put", "get", "remove"),
        // Implementaciones incorrectas
        new AlgConfig("BrokenQueue",            "phd.distributed.verifier.BrokenQueue",
                      "queue",  false, "offer", "poll"),
        new AlgConfig("NonLinearizableQueue",   "phd.distributed.verifier.NonLinearizableQueue",
                      "queue",  false, "offer", "poll"),
        new AlgConfig("PartialSyncQueue","phd.distributed.verifier.PartialSyncQueue","queue", false,"offer", "poll"),
    };

    enum SnapType { GAIsnap, RAWsnap, AspectJ }

    // ── Records de resultados ─────────────────────────────────────────────
    record ResultsC(long[][][] gai, long[][][] raw, long[][][] aj) {}
    record ResultsD(VerdictStats[] gai, VerdictStats[] raw, VerdictStats[] aj) {}
    record ResultsE(TableERow[] rows) {}

    record VerdictStats(int trueCount, int falseCount, int errorCount) {
        int total()       { return trueCount + falseCount + errorCount; }
        double pctTrue()  { return 100.0 * trueCount  / total(); }
        double pctFalse() { return 100.0 * falseCount / total(); }
        double pctError() { return 100.0 * errorCount / total(); }
        String fmt() {
            return String.format("%5.1f%% T | %5.1f%% F | %5.1f%% E",
                    pctTrue(), pctFalse(), pctError());
        }
    }

    record TableERow(String name, boolean expectedLinearizable,
                     VerdictStats gai, VerdictStats raw, VerdictStats aj) {
        String matchGai() { return match(gai, expectedLinearizable); }
        String matchRaw() { return match(raw, expectedLinearizable); }
        String matchAj()  { return match(aj,  expectedLinearizable); }

        private static String match(VerdictStats s, boolean expected) {
            if (s == null) return "ERR";
            boolean detected = expected ? s.pctTrue() > 50 : s.pctFalse() > 50;
            return detected ? "OK" : "FAIL";
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        printHeader();

        boolean onlyC  = hasArg(args, "--only=tableC");
        boolean onlyD  = hasArg(args, "--only=tableD");
        boolean onlyE  = hasArg(args, "--only=tableE");
        
        String brokenClass = parseArg(args, "--broken");
        boolean broken     = brokenClass != null;
        String dClass      = broken ? brokenClass : ALG_CLASS;
        String dType    = ALG_TYPE;
        String[] dMethods = METHODS;

        if (broken) System.out.println(
            "  Mode: BROKEN implementation (expected: NOT linearizable)\n");

        ResultsC rc = null;
        ResultsD rd = null;
        ResultsE re = null;

        if (!onlyD && !onlyE) rc = runTableC();
        if (!onlyC && !onlyE) rd = runTableDWith(dClass, dType, dMethods);
        if (!onlyC && !onlyD) re = runTableE();

        printFooter();
        writeReport(rc, rd, re, args);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TABLE C
    // ═════════════════════════════════════════════════════════════════════

    static ResultsC runTableC() {
        int T = THREADS_SWEEP_C.length;
        int O = OPERATIONS_SWEEP.length;

        long[][][] gai = new long[T][O][];
        long[][][] raw = new long[T][O][];
        long[][][] aj  = new long[T][O][];

        System.out.println("═".repeat(80));
        System.out.println("  Table C — Instrumentation overhead");
        System.out.println("═".repeat(80));

        for (int ti = 0; ti < T; ti++) {
            int threads = THREADS_SWEEP_C[ti];
            System.out.printf("%n  ── Threads = %d ──%n", threads);

            for (int oi = 0; oi < O; oi++) {
                int ops = OPERATIONS_SWEEP[oi];
                for (int w = 0; w < WARMUP_ROUNDS; w++) {
                    runProducersOnly(threads, ops, SnapType.GAIsnap);
                    runProducersOnly(threads, ops, SnapType.RAWsnap);
                    runProducersOnly(threads, ops, SnapType.AspectJ);
                }
                gai[ti][oi] = collect(threads, ops, SnapType.GAIsnap);
                raw[ti][oi] = collect(threads, ops, SnapType.RAWsnap);
                aj[ti][oi]  = collect(threads, ops, SnapType.AspectJ);
                System.out.printf("  ops=%-8d  GAI: %-20s  RAW: %-20s  AJ: %s%n",
                    ops, fmtStats(gai[ti][oi]), fmtStats(raw[ti][oi]), fmtStats(aj[ti][oi]));
            }
        }
        return new ResultsC(gai, raw, aj);
    }

    static long[] collect(int threads, int ops, SnapType type) {
        long[] times = new long[MEASURED_ROUNDS];
        for (int i = 0; i < MEASURED_ROUNDS; i++)
            times[i] = runProducersOnly(threads, ops, type);
        return times;
    }

    static long runProducersOnly(int threads, int ops, SnapType type) {
        try {
            DistAlgorithm alg = new A(ALG_CLASS, METHODS);
            long start = System.nanoTime();
            switch (type) {
                case GAIsnap -> new Executioner(threads, ops, alg, ALG_TYPE, "gAIsnap")
                                    .taskProducers();
                case RAWsnap -> new Executioner(threads, ops, alg, ALG_TYPE, "rawsnap")
                                    .taskProducers();
                case AspectJ -> {
                    LinearizabilityMonitorAspect.ACTIVE = true;
                    NativeTraceCollector.clear();
                    NativeAspectJSnapshot dummy = new NativeAspectJSnapshot();
                    new Executioner(threads, ops, alg, ALG_TYPE, dummy).taskProducers();
                    LinearizabilityMonitorAspect.ACTIVE = false;
                    NativeTraceCollector.buildPersistentVector();
                }
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } catch (Exception e) { return -1; }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TABLE D
    // ═════════════════════════════════════════════════════════════════════

    static ResultsD runTableD() {
        return runTableDWith(ALG_CLASS, ALG_TYPE, METHODS);
    }

    static ResultsD runTableDWith(String algClass, String algType, String[] methods) {
        VerdictStats[] gai = new VerdictStats[THREADS_VERDICT.length];
        VerdictStats[] raw = new VerdictStats[THREADS_VERDICT.length];
        VerdictStats[] aj  = new VerdictStats[THREADS_VERDICT.length];

        System.out.println("\n" + "═".repeat(80));
        System.out.printf("  Table D — Verdict accuracy  [%s]%n", algClass);
        System.out.printf("  ops=%d, runs=%d%n", VERDICT_OPS, VERDICT_RUNS);
        System.out.println("═".repeat(80));

        for (int i = 0; i < THREADS_VERDICT.length; i++) {
            int t = THREADS_VERDICT[i];
            gai[i] = collectVerdictsFor(t, SnapType.GAIsnap, algClass, algType, methods);
            raw[i] = collectVerdictsFor(t, SnapType.RAWsnap, algClass, algType, methods);
            aj[i]  = collectVerdictsFor(t, SnapType.AspectJ,  algClass, algType, methods);
            System.out.printf("  threads=%-4d  GAI: %s  RAW: %s  AJ: %s%n",
                t, gai[i].fmt(), raw[i].fmt(), aj[i].fmt());
        }
        return new ResultsD(gai, raw, aj);
    }

    static VerdictStats collectVerdicts(int threads, SnapType type) {
        return collectVerdictsFor(threads, type, ALG_CLASS, ALG_TYPE, METHODS);
    }

    static VerdictStats collectVerdictsFor(int threads, SnapType type,
                                            String algClass, String algType,
                                            String[] methods) {
        int trueC = 0, falseC = 0, errorC = 0;
        for (int i = 0; i < VERDICT_RUNS; i++) {
            Boolean v = runFullPipelineFor(threads, VERDICT_OPS, type,
                                           algClass, algType, methods);
            if (v == null)   errorC++;
            else if (v)      trueC++;
            else             falseC++;
        }
        return new VerdictStats(trueC, falseC, errorC);
    }

    static Boolean runFullPipeline(int threads, int ops, SnapType type) {
        return runFullPipelineFor(threads, ops, type, ALG_CLASS, ALG_TYPE, METHODS);
    }

    static Boolean runFullPipelineFor(int threads, int ops, SnapType type,
                                       String algClass, String algType,
                                       String[] methods) {
        try {
            DistAlgorithm alg = new A(algClass, methods);
            Verifier verifier = new Verifier();

            return switch (type) {
                case GAIsnap -> {
                    Executioner exec = new Executioner(threads, ops, alg, algType, "gAIsnap");
                    exec.taskProducers();
                    yield verifier.verifyDirectTrace(exec.getTrace(), algType);
                }
                case RAWsnap -> {
                    Executioner exec = new Executioner(threads, ops, alg, algType, "rawsnap");
                    exec.taskProducers();
                    yield verifier.verifyDirectTrace(exec.getTrace(), algType);
                }
                case AspectJ -> {
                    LinearizabilityMonitorAspect.ACTIVE = true;
                    NativeTraceCollector.clear();
                    NativeAspectJSnapshot dummy = new NativeAspectJSnapshot();
                    new Executioner(threads, ops, alg, algType, dummy).taskProducers();
                    LinearizabilityMonitorAspect.ACTIVE = false;
                    IPersistentVector xe = NativeTraceCollector.buildPersistentVector();
                    if (xe == null || xe.count() == 0) yield null;
                    yield verifier.verifyDirectTrace(xe, algType);
                }
            };
        } catch (Exception e) { return null; }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TABLE E — Todas las implementaciones, una corrida por celda
    // ═════════════════════════════════════════════════════════════════════

    static ResultsE runTableE() {
        System.out.println("\n" + "═".repeat(80));
        System.out.printf("  Table E — All implementations  [threads=%d, ops=%d, runs=%d]%n",
            TABLE_E_THREADS, TABLE_E_OPS, TABLE_E_RUNS);
        System.out.println("═".repeat(80));

        TableERow[] rows = new TableERow[TABLE_E_ALGORITHMS.length];

        for (int i = 0; i < TABLE_E_ALGORITHMS.length; i++) {
            AlgConfig c = TABLE_E_ALGORITHMS[i];

            VerdictStats gai = collectVerdictsTableE(c, SnapType.GAIsnap);
            VerdictStats raw = collectVerdictsTableE(c, SnapType.RAWsnap);
            VerdictStats aj  = collectVerdictsTableE(c, SnapType.AspectJ);

            rows[i] = new TableERow(c.name(), c.expectedLinearizable(), gai, raw, aj);

            System.out.printf("  %-30s  expected=%-8s  GAI:%s  RAW:%s  AJ:%s%n",
                c.name(),
                c.expectedLinearizable() ? "LIN" : "NOT LIN",
                rows[i].matchGai(), rows[i].matchRaw(), rows[i].matchAj());
        }
        return new ResultsE(rows);
    }

    static VerdictStats collectVerdictsTableE(AlgConfig c, SnapType type) {
        int trueC = 0, falseC = 0, errorC = 0;
        for (int i = 0; i < TABLE_E_RUNS; i++) {
            Boolean v = runFullPipelineFor(TABLE_E_THREADS, TABLE_E_OPS, type,
                                           c.clazz(), c.type(), c.methods());
            if (v == null)  errorC++;
            else if (v)     trueC++;
            else            falseC++;
        }
        return new VerdictStats(trueC, falseC, errorC);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REPORTE
    // ═════════════════════════════════════════════════════════════════════

    static void writeReport(ResultsC rc, ResultsD rd, ResultsE re, String[] args) {
        ReportWriter.Format fmt = ReportWriter.parseFormat(args);
        String defaultName = "verifier-benchmark" + ReportWriter.extension(fmt);
        Path out = ReportWriter.parseOutput(args, defaultName);

        if (args.length == 0) return;

        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
        } catch (IOException e) {
            System.err.println("ERROR creando directorio: " + e.getMessage());
            return;
        }

        try (ReportWriter w = new ReportWriter(out, fmt)) {

            w.title("VerifierBenchmark — Instrumentation overhead + Verdict accuracy");
            w.metadata("Algorithm",     ALG_CLASS);
            w.metadata("Methodology",   "Georges et al. (OOPSLA 2007)");
            w.metadata("Warmup runs",   String.valueOf(WARMUP_ROUNDS));
            w.metadata("Measured runs", String.valueOf(MEASURED_ROUNDS));
            w.metadata("OS",   System.getProperty("os.name"));
            w.metadata("CPUs", String.valueOf(Runtime.getRuntime().availableProcessors()));
            w.metadata("JVM",  System.getProperty("java.vm.name") + " " +
                               System.getProperty("java.version"));
            w.blank();

            // Table C
            if (rc != null) {
                for (int ti = 0; ti < THREADS_SWEEP_C.length; ti++) {
                    int threads = THREADS_SWEEP_C[ti];
                    w.section("Table C — Instrumentation overhead (threads=" + threads + ")");
                    w.text("taskProducers() only. Format: mean / min / max (ms).");

                    List<String> cols = List.of("Ops",
                        "GAIsnap mean", "GAIsnap min", "GAIsnap max",
                        "RAWsnap mean", "RAWsnap min", "RAWsnap max",
                        "AspectJ mean", "AspectJ min", "AspectJ max");

                    List<List<String>> rows = new java.util.ArrayList<>();
                    for (int oi = 0; oi < OPERATIONS_SWEEP.length; oi++) {
                        long[] g = rc.gai[ti][oi], r = rc.raw[ti][oi], a = rc.aj[ti][oi];
                        rows.add(List.of(String.valueOf(OPERATIONS_SWEEP[oi]),
                            mean(g), min(g), max(g),
                            mean(r), min(r), max(r),
                            mean(a), min(a), max(a)));
                    }
                    w.table("Threads=" + threads, cols, rows, 0,1,2,3,4,5,6,7,8,9);
                    w.blank();
                }
            }

            // Table D
            if (rd != null) {
                w.section("Table D — Verdict comparison (ops=" + VERDICT_OPS + ")");
                w.text("Full pipeline. " + VERDICT_RUNS + " runs per cell. " +
                       "Reference: El-Hokayem & Falcone (RV 2018).");

                List<String> cols = List.of("Threads",
                    "GAI %True", "GAI %False", "GAI %Error",
                    "RAW %True", "RAW %False", "RAW %Error",
                    "AJ %True",  "AJ %False",  "AJ %Error");

                List<List<String>> rows = new java.util.ArrayList<>();
                for (int i = 0; i < THREADS_VERDICT.length; i++) {
                    VerdictStats g = rd.gai[i], r = rd.raw[i], a = rd.aj[i];
                    rows.add(List.of(String.valueOf(THREADS_VERDICT[i]),
                        pct(g.pctTrue()),  pct(g.pctFalse()),  pct(g.pctError()),
                        pct(r.pctTrue()),  pct(r.pctFalse()),  pct(r.pctError()),
                        pct(a.pctTrue()),  pct(a.pctFalse()),  pct(a.pctError())));
                }
                w.table("Verdict accuracy vs threads", cols, rows, 0,1,2,3,4,5,6,7,8,9);
                w.blank();
            }

            // Table E
            if (re != null) {
                w.section("Table E — All implementations (threads=" + TABLE_E_THREADS +
                          ", ops=" + TABLE_E_OPS + ", runs=" + TABLE_E_RUNS + ")");
                w.text("One verdict per cell. OK = matches expected. FAIL = unexpected verdict.");

                List<String> cols = List.of(
                    "Implementation", "Expected",
                    "GAI %True", "GAI %False", "GAI Match",
                    "RAW %True", "RAW %False", "RAW Match",
                    "AJ %True",  "AJ %False",  "AJ Match");

                List<List<String>> rows = new java.util.ArrayList<>();
                for (TableERow row : re.rows()) {
                    rows.add(List.of(
                        row.name(),
                        row.expectedLinearizable() ? "LIN" : "NOT LIN",
                        pct(row.gai().pctTrue()), pct(row.gai().pctFalse()), row.matchGai(),
                        pct(row.raw().pctTrue()), pct(row.raw().pctFalse()), row.matchRaw(),
                        pct(row.aj().pctTrue()),  pct(row.aj().pctFalse()),  row.matchAj()));
                }
                w.table("All implementations — correctness and detection", cols, rows,
                        0,1,2,3,4,5,6,7,8,9,10);
            }

        } catch (IOException e) {
            System.err.println("ERROR writing report: " + e.getMessage());
            return;
        }
        System.out.println("\nReport written to: " + out.toAbsolutePath());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static DistAlgorithm buildAlgorithm() {
        try { return new A(ALG_CLASS, METHODS); }
        catch (Exception e) { return null; }
    }

    static String fmtStats(long[] t) {
        long s=0, mn=Long.MAX_VALUE, mx=Long.MIN_VALUE; int n=0;
        for (long v:t) if(v>=0){s+=v; mn=Math.min(mn,v); mx=Math.max(mx,v); n++;}
        return n==0 ? "TIMEOUT" : String.format("%4d/%3d/%3d", s/n, mn, mx);
    }
    static String mean(long[] t){long s=0;int n=0;for(long v:t)if(v>=0){s+=v;n++;}return n==0?"N/A":String.valueOf(s/n);}
    static String min(long[] t) {long m=Long.MAX_VALUE;for(long v:t)if(v>=0&&v<m)m=v;return m==Long.MAX_VALUE?"N/A":String.valueOf(m);}
    static String max(long[] t) {long m=Long.MIN_VALUE;for(long v:t)if(v>=0&&v>m)m=v;return m==Long.MIN_VALUE?"N/A":String.valueOf(m);}
    static String pct(double v) {return String.format("%.1f%%",v);}

    static boolean hasArg(String[] args, String prefix) {
        for (String a:args) if(a.startsWith(prefix)) return true;
        return false;
    }
    static String parseArg(String[] args, String prefix) {
        for (String a : args) {
            if (a.equals(prefix))
                return "phd.distributed.verifier.BrokenQueue"; // default si no tiene valor
            if (a.startsWith(prefix + "="))
                return a.substring(prefix.length() + 1);
        }
        return null;
    }

    static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  VerifierBenchmark — Overhead + Verdict accuracy + All impls     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf("  Methodology: Georges et al. (OOPSLA 2007) — warmup %d, measured %d%n%n",
                WARMUP_ROUNDS, MEASURED_ROUNDS);
    }

    static void printFooter() {
        System.out.printf("%n  OS  : %s%n", System.getProperty("os.name"));
        System.out.printf("  CPUs: %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("  JVM : %s %s%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"));
    }
}
