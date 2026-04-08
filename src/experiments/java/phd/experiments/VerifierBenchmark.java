package phd.experiments;

import clojure.lang.IPersistentVector;
import phd.distributed.api.A;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.core.Executioner;
import phd.distributed.core.Verifier;
import phd.experiments.aspectj.AspectJSnapshot;
import phd.experiments.aspectj.LinearizabilityMonitorAspect;
import phd.experiments.aspectj.NativeAspectJSnapshot;
import phd.experiments.aspectj.NativeTraceCollector;

import java.nio.file.Files;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * VerifierBenchmark — dos tablas independientes:
 *
 *  Table C: overhead de instrumentación pura (taskProducers sin verificar).
 *           Barrido hasta 100 000 ops. Sigue Georges et al. (OOPSLA 2007):
 *           WARMUP_ROUNDS descartados + MEASURED_ROUNDS medidos → media ± rango.
 *
 *  Table D: comparación de veredictos (linearizable vs no).
 *           Máximo 100 ops porque verificar es NP-completo.
 *           VERDICT_RUNS ejecuciones completas (instrumentación + verificación).
 *           Reporta: %TRUE | %FALSE | %ERROR para cada estrategia.
 *           Referencia directa a El-Hokayem & Falcone (RV 2018), Tabla slide 12.
 */
public class VerifierBenchmark {

    // ── Parámetros Georges et al. ─────────────────────────────────────────
    static final int WARMUP_ROUNDS   = 5;
    static final int MEASURED_ROUNDS = 10;

    record ResultsC(long[][][] gai, long[][][] raw, long[][][] aj) {}
    record ResultsD(VerdictStats[] gai, VerdictStats[] raw, VerdictStats[] aj) {}

   // ── Parámetros Table C ────────────────────────────────────────────────
    static final int[] OPERATIONS_SWEEP = {
        100, 500, 1_000, 5_000, 10_000, 50_000, 100_000
    };
    static final int[] THREADS_SWEEP_C = { 2, 4, 8, 16, 32, 64 };

    // ── Parámetros Table D (veredictos) ───────────────────────────────────
    // 100 ops máximo porque la verificación de linearizabilidad es NP-completo.
    // Con más operaciones el verificador puede tardar minutos o no terminar.
    static final int VERDICT_OPS   = 100;
    static final int VERDICT_RUNS  = 100;
    static final int[] THREADS_VERDICT = { 2, 4, 8, 16, 32, 64 };

    // ── Algoritmo ─────────────────────────────────────────────────────────
    static final String   ALG_CLASS = "java.util.concurrent.ConcurrentLinkedQueue";
    static final String   ALG_TYPE  = "queue";
    static final String[] METHODS   = { "offer", "poll" };

    enum SnapType { GAIsnap, RAWsnap, AspectJ }

    // ── Entry point ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        printHeader();

        boolean onlyC = hasArg(args, "--only=tableC");
        boolean onlyD = hasArg(args, "--only=tableD");

        ResultsC rc = null;
        ResultsD rd = null;

        if (!onlyD) rc = runTableC();  // solo si no es --only=tableD
        if (!onlyC) rd = runTableD();  // solo si no es --only=tableC

        printFooter();
        writeReport(rc, rd, args);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TABLE C — Overhead de instrumentación, sin verificar
    // ═════════════════════════════════════════════════════════════════════

    static ResultsC runTableC() {
        // ResultsC ahora es [threads][ops] en lugar de [ops]
        int T = THREADS_SWEEP_C.length;
        int O = OPERATIONS_SWEEP.length;

        long[][][] gai = new long[T][O][];
        long[][][] raw = new long[T][O][];
        long[][][] aj  = new long[T][O][];

        //System.out.println("═".repeat(100));
        // System.out.println("  Table C — Instrumentation overhead vs. operations AND threads");
        // System.out.printf ("            Warmup: %d | Measured: %d runs%n", WARMUP_ROUNDS, MEASURED_ROUNDS);
        // System.out.println("═".repeat(100));

        for (int ti = 0; ti < T; ti++) {
            int threads = THREADS_SWEEP_C[ti];
            // System.out.printf("%n  ── Threads = %d ──%n", threads);
            // System.out.printf("  %-10s  %-26s  %-26s  %-26s%n",
            //         "Ops", "GAIsnap mean/min/max", "RAWsnap mean/min/max", "AspectJ mean/min/max");

            for (int oi = 0; oi < O; oi++) {
                int ops = OPERATIONS_SWEEP[oi];

                // warmup
                for (int w = 0; w < WARMUP_ROUNDS; w++) {
                    runProducersOnly(threads, ops, SnapType.GAIsnap);
                    runProducersOnly(threads, ops, SnapType.RAWsnap);
                    runProducersOnly(threads, ops, SnapType.AspectJ);
                }

                gai[ti][oi] = collect(threads, ops, SnapType.GAIsnap);
                raw[ti][oi] = collect(threads, ops, SnapType.RAWsnap);
                aj[ti][oi]  = collect(threads, ops, SnapType.AspectJ);

                // System.out.printf("  %-10d  %-26s  %-26s  %-26s%n",
                //         ops,
                //         fmtStats(gai[ti][oi]),
                //         fmtStats(raw[ti][oi]),
                //         fmtStats(aj[ti][oi]));
            }
        }

        return new ResultsC(gai, raw, aj);
    }
    /** Corre MEASURED_ROUNDS veces taskProducers() y devuelve los tiempos en ms. */
    static long[] collect(int threads, int ops, SnapType type) {
        long[] times = new long[MEASURED_ROUNDS];
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            times[i] = runProducersOnly(threads, ops, type);
        }
        return times;
    }

    /** Una sola ejecución de taskProducers(), devuelve ms o -1 si falla. */
    static long runProducersOnly(int threads, int ops, SnapType type) {
        try {
            DistAlgorithm alg = buildAlgorithm();
            if (alg == null) return -1;

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
                    // Construimos el vector pero NO verificamos — solo overhead de captura
                    NativeTraceCollector.buildPersistentVector();
                }
            }

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } catch (Exception e) {
            return -1;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TABLE D — Comparación de veredictos (linearizable vs no)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Estructura de resultado para Table D.
     * trueCount  = veces que el verificador dijo "linearizable"
     * falseCount = veces que dijo "no linearizable"
     * errorCount = timeout, excepción o traza vacía
     */
    record VerdictStats(int trueCount, int falseCount, int errorCount) {
        int total()      { return trueCount + falseCount + errorCount; }
        double pctTrue() { return 100.0 * trueCount  / total(); }
        double pctFalse(){ return 100.0 * falseCount / total(); }
        double pctError(){ return 100.0 * errorCount / total(); }
        String fmt() {
            return String.format("%5.1f%% T | %5.1f%% F | %5.1f%% E",
                    pctTrue(), pctFalse(), pctError());
        }
    }

    static ResultsD runTableD() {
        VerdictStats[] gai = new VerdictStats[THREADS_VERDICT.length];
        VerdictStats[] raw = new VerdictStats[THREADS_VERDICT.length];
        VerdictStats[] aj  = new VerdictStats[THREADS_VERDICT.length];

        for (int i = 0; i < THREADS_VERDICT.length; i++) {
            int t = THREADS_VERDICT[i];
            gai[i] = collectVerdicts(t, SnapType.GAIsnap);
            raw[i] = collectVerdicts(t, SnapType.RAWsnap);
            aj[i]  = collectVerdicts(t, SnapType.AspectJ);
            System.out.printf("%-10d  %-36s  %-36s  %-36s%n",
                t, gai[i].fmt(), raw[i].fmt(), aj[i].fmt());
        }
        return new ResultsD(gai, raw, aj);
    }

    /** Corre VERDICT_RUNS ejecuciones completas (instrumentación + verificación). */
    static VerdictStats collectVerdicts(int threads, SnapType type) {
        int trueC = 0, falseC = 0, errorC = 0;

        for (int i = 0; i < VERDICT_RUNS; i++) {
            Boolean verdict = runFullPipeline(threads, VERDICT_OPS, type);
            if (verdict == null)       errorC++;
            else if (verdict)          trueC++;
            else                       falseC++;
        }

        return new VerdictStats(trueC, falseC, errorC);
    }

    /**
     * Ejecución completa: instrumentación + verificación.
     * Devuelve true/false según el verificador, o null si hay error/timeout.
     */
    static Boolean runFullPipeline(int threads, int ops, SnapType type) {
        try {
            DistAlgorithm alg = buildAlgorithm();
            if (alg == null) return null;
            Verifier verifier = new Verifier();

            return switch (type) {
                case GAIsnap -> {
                    Executioner exec = new Executioner(threads, ops, alg, ALG_TYPE, "gAIsnap");
                    exec.taskProducers();
                    IPersistentVector xe = exec.getTrace();      // ajusta al nombre real
                    yield verifier.verifyDirectTrace(xe, ALG_TYPE);
                }
                case RAWsnap -> {
                    Executioner exec = new Executioner(threads, ops, alg, ALG_TYPE, "rawsnap");
                    exec.taskProducers();
                    IPersistentVector xe = exec.getTrace();
                    yield verifier.verifyDirectTrace(xe, ALG_TYPE);
                }
                case AspectJ -> {
                    LinearizabilityMonitorAspect.ACTIVE = true;
                    NativeTraceCollector.clear();
                    NativeAspectJSnapshot dummy = new NativeAspectJSnapshot();
                    new Executioner(threads, ops, alg, ALG_TYPE, dummy).taskProducers();
                    LinearizabilityMonitorAspect.ACTIVE = false;
                    IPersistentVector xe = NativeTraceCollector.buildPersistentVector();
                    if (xe == null || xe.count() == 0) yield null;
                    yield verifier.verifyDirectTrace(xe, ALG_TYPE);
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static DistAlgorithm buildAlgorithm() {
        try { return new A(ALG_CLASS, METHODS); }
        catch (Exception e) { return null; }
    }

    /** media / min / max en ms desde un arreglo de mediciones. */
    static String fmtStats(long[] times) {
        long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        int good = 0;
        for (long t : times) {
            if (t >= 0) { sum += t; min = Math.min(min, t); max = Math.max(max, t); good++; }
        }
        if (good == 0) return "TIMEOUT";
        return String.format("%4d / %3d / %3d", sum / good, min, max);
    }

    static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  VerifierBenchmark — Instrumentation overhead + Verdict accuracy  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf ("  Methodology: Georges et al. (OOPSLA 2007) — warmup %d, measured %d%n%n",
                WARMUP_ROUNDS, MEASURED_ROUNDS);
    }

    static void printFooter() {
        System.out.printf("  OS  : %s%n", System.getProperty("os.name"));
        System.out.printf("  CPUs: %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("  JVM : %s %s%n",
                System.getProperty("java.vm.name"),
                System.getProperty("java.version"));
    }

    // ── Escritura del reporte ─────────────────────────────────────────────

    /**
     * Llama esto desde main() después de runTableC() y runTableD():
     *
     *   ResultsC rc = runTableC();
     *   ResultsD rd = runTableD();
     *   writeReport(rc, rd, args);
     */
    static void writeReport(ResultsC rc, ResultsD rd, String[] args) {
    ReportWriter.Format fmt = ReportWriter.parseFormat(args);
    String defaultName = "verifier-benchmark" + ReportWriter.extension(fmt);
    Path out = ReportWriter.parseOutput(args, defaultName);

    boolean write = args.length > 0;
    if (!write) return;

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

        // ── Table C — solo si rc no es null ───────────────────────────
        if (rc != null) {
            for (int ti = 0; ti < THREADS_SWEEP_C.length; ti++) {
                int threads = THREADS_SWEEP_C[ti];

                w.section("Table C — Instrumentation overhead (threads=" + threads + ")");
                w.text("taskProducers() only. Format: mean / min / max (ms).");

                List<String> colsC = List.of(
                    "Ops",
                    "GAIsnap mean", "GAIsnap min", "GAIsnap max",
                    "RAWsnap mean", "RAWsnap min", "RAWsnap max",
                    "AspectJ mean", "AspectJ min", "AspectJ max");

                List<List<String>> rowsC = new java.util.ArrayList<>();
                for (int oi = 0; oi < OPERATIONS_SWEEP.length; oi++) {
                    long[] g = rc.gai[ti][oi];
                    long[] r = rc.raw[ti][oi];
                    long[] a = rc.aj[ti][oi];
                    rowsC.add(List.of(
                        String.valueOf(OPERATIONS_SWEEP[oi]),
                        mean(g), min(g), max(g),
                        mean(r), min(r), max(r),
                        mean(a), min(a), max(a)
                    ));
                }
                w.table("Threads=" + threads + " — instrumentation cost vs operations",
                        colsC, rowsC, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
                w.blank();
            }
        }

        // ── Table D — solo si rd no es null ───────────────────────────
        if (rd != null) {
            w.section("Table D — Verdict comparison (ops=" + VERDICT_OPS + ")");
            w.text("Full pipeline (instrumentation + verification). " +
                   VERDICT_RUNS + " runs per cell. " +
                   "Reference: El-Hokayem & Falcone (RV 2018).");

            List<String> colsD = List.of(
                "Threads",
                "GAI %True", "GAI %False", "GAI %Error",
                "RAW %True", "RAW %False", "RAW %Error",
                "AJ %True",  "AJ %False",  "AJ %Error");

            List<List<String>> rowsD = new java.util.ArrayList<>();
            for (int i = 0; i < THREADS_VERDICT.length; i++) {
                VerdictStats g = rd.gai[i], r = rd.raw[i], a = rd.aj[i];
                rowsD.add(List.of(
                    String.valueOf(THREADS_VERDICT[i]),
                    pct(g.pctTrue()),  pct(g.pctFalse()),  pct(g.pctError()),
                    pct(r.pctTrue()),  pct(r.pctFalse()),  pct(r.pctError()),
                    pct(a.pctTrue()),  pct(a.pctFalse()),  pct(a.pctError())
                ));
            }
            w.table("Verdict accuracy vs threads", colsD, rowsD,
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        }

    } catch (IOException e) {
        System.err.println("ERROR writing report: " + e.getMessage());
        return;
    }
    System.out.println("Report written to: " + out.toAbsolutePath());
}

    // helpers de formato para el reporte
    static String mean(long[] t) { long s=0; int n=0; for(long v:t) if(v>=0){s+=v;n++;} return n==0?"N/A":String.valueOf(s/n); }
    static String min(long[] t)  { long m=Long.MAX_VALUE; for(long v:t) if(v>=0&&v<m)m=v; return m==Long.MAX_VALUE?"N/A":String.valueOf(m); }
    static String max(long[] t)  { long m=Long.MIN_VALUE; for(long v:t) if(v>=0&&v>m)m=v; return m==Long.MIN_VALUE?"N/A":String.valueOf(m); }
    static String pct(double v)  { return String.format("%.1f%%", v); }

    static boolean hasArg(String[] args, String prefix) {
        for (String a : args) if (a.startsWith(prefix)) return true;
        return false;
    }
}