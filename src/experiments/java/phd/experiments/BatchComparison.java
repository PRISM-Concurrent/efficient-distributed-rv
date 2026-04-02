package phd.experiments;

import phd.distributed.api.A;
import phd.distributed.api.AlgorithmLibrary;
import phd.distributed.api.DistAlgorithm;
import phd.distributed.api.VerificationFramework;
import phd.distributed.api.VerificationResult;
import phd.distributed.core.Executioner;
import phd.experiments.aspectj.AspectJSnapshot;

import java.util.concurrent.TimeUnit;

/**
 * BatchComparison — experimental comparison of three trace-collection strategies:
 *
 *   1. GAIsnap  (CollectFAInc)  — explicit snapshot via atomic counter
 *   2. RAWsnap  (CollectRAW)    — explicit snapshot via Clojure collect object
 *   3. AspectJ                  — automatic collection via bytecode interception
 *
 * Two tables are produced:
 *
 *   Table 1 – Instrumentation only (taskProducers phase)
 *             Isolates the cost of the trace-collection mechanism.
 *
 *   Table 2 – Full verification pipeline (taskProducers + taskVerifiers)
 *             End-to-end cost including the JitLin linearizability checker.
 *
 * ── How to run ────────────────────────────────────────────────────────────
 *
 *   Build:
 *     mvn -q package -DskipTests
 *
 *   GAIsnap / RAWsnap only (no AspectJ agent needed):
 *     java -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
 *          phd.experiments.BatchComparison
 *
 *   Full comparison including AspectJ:
 *     java -javaagent:~/.m2/repository/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar \
 *          -cp target/efficient-distributed-rv-*-jar-with-dependencies.jar \
 *          phd.experiments.BatchComparison
 *
 * ── Notes ─────────────────────────────────────────────────────────────────
 *   - WARMUP_ROUNDS runs are performed before each measurement series to
 *     allow the JVM and the Clojure runtime to reach a steady state.
 *   - Reported times are averages over MEASURED_ROUNDS runs.
 *   - The AspectJ column appears as N/A if the -javaagent is not present
 *     (the aspect is inactive and returns identical numbers to GAIsnap).
 */
public class BatchComparison {

    // ── Experiment parameters ──────────────────────────────────────────────
    static final int THREADS         = 4;
    static final int OPERATIONS      = 100;
    static final int WARMUP_ROUNDS   = 5;
    static final int MEASURED_ROUNDS = 10;

    // ── Algorithm descriptors ──────────────────────────────────────────────
    record AlgConfig(String shortName, String type, String[] methods) {}

    static final AlgConfig[] ALGORITHMS = {
        new AlgConfig("ConcurrentLinkedQueue",  "queue",
                      new String[]{"offer", "poll"}),
        new AlgConfig("ConcurrentHashMap",       "map",
                      new String[]{"put", "get", "remove"}),
        new AlgConfig("ConcurrentLinkedDeque",   "deque",
                      new String[]{"offerFirst", "offerLast", "pollFirst", "pollLast"}),
        new AlgConfig("LinkedBlockingQueue",     "queue",
                      new String[]{"offer", "poll"}),
        new AlgConfig("ConcurrentSkipListSet",   "set",
                      new String[]{"add", "remove", "contains"}),
        new AlgConfig("LinkedTransferQueue",     "queue",
                      new String[]{"offer", "poll"}),
        new AlgConfig("ConcurrentSkipListMap",   "map",
                      new String[]{"put", "get", "remove"}),
        new AlgConfig("LinkedBlockingDeque",     "deque",
                      new String[]{"offerFirst", "offerLast", "pollFirst", "pollLast"})
    };

    // ── Internal result holder ─────────────────────────────────────────────
    /**
     * @param prodMs    average producers phase (ms)
     * @param verMs     average verifier phase (ms)
     * @param linCount  number of runs (out of totalRuns) that returned linearizable
     * @param totalRuns number of successful measured runs
     */
    record Measurement(long prodMs, long verMs, int linCount, int totalRuns) {
        long totalMs() { return prodMs + verMs; }

        /** "10/10 LIN", "0/10 NON", "7/10 ?" */
        String verdictStr() {
            if (totalRuns == 0) return "  ERROR  ";
            String tag = linCount == totalRuns ? "LIN" :
                         linCount == 0         ? "NON" : " ? ";
            return String.format("%2d/%2d %s", linCount, totalRuns, tag);
        }

        /** true only when all runs agreed on the same verdict */
        boolean unanimous() { return totalRuns > 0 && (linCount == 0 || linCount == totalRuns); }
    }

    // ── Entry point ────────────────────────────────────────────────────────
    public static void main(String[] args) {
        printHeader();

        // results[algorithm][mode: 0=GAI, 1=RAW, 2=AspectJ]
        Measurement[][] results = new Measurement[ALGORITHMS.length][3];

        for (int i = 0; i < ALGORITHMS.length; i++) {
            AlgConfig alg = ALGORITHMS[i];
            System.out.printf("  %-30s", alg.shortName + " ...");
            System.out.flush();

            results[i][0] = measureSnap(alg, "gAIsnap");
            results[i][1] = measureSnap(alg, "rawsnap");
            results[i][2] = measureAspectJ(alg);

            System.out.printf("GAI %4d ms  RAW %4d ms  AJ %4d ms%n",
                ms(results[i][0]), ms(results[i][1]), ms(results[i][2]));
        }

        printInstrumentationTable(results);
        printFullPipelineTable(results);
        printVerdictTable(results);
        printFooter();
    }

    // ── Per-strategy measurement ───────────────────────────────────────────

    /**
     * GAIsnap / RAWsnap: uses the existing VerificationFramework.
     * Warm-up runs are discarded; MEASURED_ROUNDS are averaged.
     */
    static Measurement measureSnap(AlgConfig alg, String snapType) {
        for (int w = 0; w < WARMUP_ROUNDS; w++) runSnap(alg, snapType);

        long sumProd = 0, sumVer = 0;
        int  ok = 0, linCount = 0;
        for (int m = 0; m < MEASURED_ROUNDS; m++) {
            Measurement r = runSnap(alg, snapType);
            if (r != null) {
                sumProd  += r.prodMs;
                sumVer   += r.verMs;
                linCount += r.linCount;
                ok++;
            }
        }
        return ok == 0 ? null : new Measurement(sumProd / ok, sumVer / ok, linCount, ok);
    }

    static Measurement runSnap(AlgConfig alg, String snapType) {
        try {
            AlgorithmLibrary.AlgorithmInfo info =
                AlgorithmLibrary.getInfo(alg.shortName);
            if (info == null) return null;

            VerificationResult r = VerificationFramework
                .verify(info.getImplementationClass())
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(alg.type)
                .withMethods(alg.methods)
                .withSnapshot(snapType)
                .run();

            return new Measurement(
                r.getProdExecutionTime().toMillis(),
                r.getVerifierExecutionTime().toMillis(),
                r.isLinearizable() ? 1 : 0,
                1
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * AspectJ mode: creates AspectJSnapshot and uses Executioner directly.
     *
     * Flow:
     *   1. new AspectJSnapshot(threads) → resets logrAw state, registers
     *      itself as AspectJSnapshot.current (read by the aspect).
     *   2. Executioner is built with the snapshot instead of a snap-type string.
     *   3. taskProducers() runs — the aspect intercepts Method.invoke() calls
     *      in A.apply() and populates logrAw state.
     *   4. taskVerifiers() reads from logrAw via AspectJSnapshot.buildXE().
     */
 
    static Measurement measureAspectJ(AlgConfig alg) {
        for (int w = 0; w < WARMUP_ROUNDS; w++) runAspectJ(alg);

        long sumProd = 0, sumVer = 0;
        int  ok = 0, linCount = 0;
        for (int m = 0; m < MEASURED_ROUNDS; m++) {
            Measurement r = runAspectJ(alg);
            if (r != null) {
                sumProd  += r.prodMs;
                sumVer   += r.verMs;
                linCount += r.linCount;
                ok++;
            }
        }
        return ok == 0 ? null : new Measurement(sumProd / ok, sumVer / ok, linCount, ok);
    }

    // <-- ESTE ES EL MÉTODO QUE CAMBIA: AHORA USA EL FRAMEWORK
    static Measurement runAspectJ(AlgConfig alg) {
        try {
            AlgorithmLibrary.AlgorithmInfo info = AlgorithmLibrary.getInfo(alg.shortName);
            if (info == null) return null;

            // Instanciamos el snapshot personalizado para el Agente
            AspectJSnapshot snap = new AspectJSnapshot(THREADS);

            // Utilizamos la misma API fluida que GAIsnap/RAWsnap
            VerificationResult r = VerificationFramework
                .verify(info.getImplementationClass())
                .withThreads(THREADS)
                .withOperations(OPERATIONS)
                .withObjectType(alg.type)
                .withMethods(alg.methods)
                .withCustomSnapshot(snap) // <-- Inyección directa
                .run();

            return new Measurement(
                r.getProdExecutionTime().toMillis(),
                r.getVerifierExecutionTime().toMillis(),
                r.isLinearizable() ? 1 : 0,
                1
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ── Table 1: Instrumentation overhead ─────────────────────────────────

    static void printInstrumentationTable(Measurement[][] results) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println(" Table 1 — Instrumentation overhead  (taskProducers only, avg of "
                           + MEASURED_ROUNDS + " runs)");
        System.out.printf("           Threads: %d  |  Operations per run: %d%n",
                          THREADS, OPERATIONS);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println("┌──────────────────────────┬────────────┬────────────┬────────────┬──────────────┐");
        System.out.println("│ Algorithm                │ GAIsnap    │ RAWsnap    │ AspectJ    │ GAI/RAW      │");
        System.out.println("├──────────────────────────┼────────────┼────────────┼────────────┼──────────────┤");

        for (int i = 0; i < ALGORITHMS.length; i++) {
            Measurement gai = results[i][0];
            Measurement raw = results[i][1];
            Measurement aj  = results[i][2];

            String gaiStr = gai == null ? "  ERROR   " : String.format("%8d ms", gai.prodMs);
            String rawStr = raw == null ? "  ERROR   " : String.format("%8d ms", raw.prodMs);
            String ajStr  = aj  == null ? "  ERROR   " : String.format("%8d ms", aj.prodMs);

            String ratio = (gai != null && raw != null && raw.prodMs > 0)
                ? String.format("%.2fx", (double) gai.prodMs / raw.prodMs)
                : "  N/A";

            System.out.printf("│ %-24s │ %s │ %s │ %s │ %12s │%n",
                ALGORITHMS[i].shortName, gaiStr, rawStr, ajStr, ratio);
        }

        System.out.println("└──────────────────────────┴────────────┴────────────┴────────────┴──────────────┘");
        System.out.println("  Ratio < 1.0 → GAIsnap faster  |  Ratio > 1.0 → RAWsnap faster");
        System.out.println("  AspectJ = N/A if -javaagent not provided");
    }

    // ── Table 2: Full verification pipeline ───────────────────────────────

    static void printFullPipelineTable(Measurement[][] results) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println(" Table 2 — Full verification pipeline  (taskProducers + taskVerifiers, avg of "
                           + MEASURED_ROUNDS + " runs)");
        System.out.printf("           Threads: %d  |  Operations per run: %d%n",
                          THREADS, OPERATIONS);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("┌──────────────────────────┬─────────────────────────────┬─────────────────────────────┬─────────────────────────────┬──────────────┐");
        System.out.println("│ Algorithm                │ GAIsnap  prod | ver | total │ RAWsnap  prod | ver | total │ AspectJ  prod | ver | total │ GAI/RAW      │");
        System.out.println("├──────────────────────────┼─────────────────────────────┼─────────────────────────────┼─────────────────────────────┼──────────────┤");

        long totalGai = 0, totalRaw = 0;
        int counted = 0;

        for (int i = 0; i < ALGORITHMS.length; i++) {
            Measurement gai = results[i][0];
            Measurement raw = results[i][1];
            Measurement aj  = results[i][2];

            if (gai == null || raw == null) {
                System.out.printf("│ %-24s │ %-27s │ %-27s │ %-27s │ %-12s │%n",
                    ALGORITHMS[i].shortName, "ERROR", "ERROR", "ERROR", "—");
                continue;
            }

            String ajCol = aj == null
                ? "   N/A  |  N/A |   N/A"
                : String.format("%5d | %4d | %5d ms", aj.prodMs, aj.verMs, aj.totalMs());

            String ratio = raw.totalMs() > 0
                ? String.format("%.2fx", (double) gai.totalMs() / raw.totalMs())
                : "N/A";

            System.out.printf("│ %-24s │ %5d | %4d | %5d ms │ %5d | %4d | %5d ms │ %s │ %12s │%n",
                ALGORITHMS[i].shortName,
                gai.prodMs, gai.verMs, gai.totalMs(),
                raw.prodMs, raw.verMs, raw.totalMs(),
                ajCol,
                ratio);

            totalGai += gai.totalMs();
            totalRaw += raw.totalMs();
            counted++;
        }

        System.out.println("├──────────────────────────┼─────────────────────────────┼─────────────────────────────┼─────────────────────────────┼──────────────┤");

        if (counted > 0) {
            String avgRatio = totalRaw > 0
                ? String.format("%.2fx", (double) totalGai / totalRaw)
                : "N/A";
            System.out.printf("│ %-24s │ %25d ms │ %25d ms │ %-27s │ %12s │%n",
                "TOTAL (" + counted + " algorithms)",
                totalGai, totalRaw, "(see per-row)", avgRatio);
        }

        System.out.println("└──────────────────────────┴─────────────────────────────┴─────────────────────────────┴─────────────────────────────┴──────────────┘");
        System.out.println("  prod = producers phase  |  ver = JitLin checker  |  Ratio = GAIsnap / RAWsnap");
    }

    // ── Table 3: Verdict comparison ────────────────────────────────────────

    /**
     * Shows the linearizability verdict of each strategy per algorithm.
     *
     * Each cell: "N/M LIN" if N out of M runs returned linearizable,
     *            "N/M NON" if all returned non-linearizable,
     *            "N/M  ? " if runs disagreed.
     *
     * AGREE column: whether all three strategies returned the same unanimous
     * verdict. Disagreement between strategies is the key soundness signal:
     * if GAIsnap/RAWsnap say LIN but AspectJ says NON (or vice-versa), it
     * indicates a difference in the observed history — worth investigating.
     */
    static void printVerdictTable(Measurement[][] results) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════════════");
        System.out.println(" Table 3 — Linearizability verdicts  (" + MEASURED_ROUNDS + " runs per strategy)");
        System.out.printf("           Threads: %d  |  Operations per run: %d%n", THREADS, OPERATIONS);
        System.out.println("════════════════════════════════════════════════════════════════════════════════");
        System.out.println("┌──────────────────────────┬────────────┬────────────┬────────────┬───────────┐");
        System.out.println("│ Algorithm                │ GAIsnap    │ RAWsnap    │ AspectJ    │ AGREE?    │");
        System.out.println("├──────────────────────────┼────────────┼────────────┼────────────┼───────────┤");

        int agreements = 0, rows = 0;

        for (int i = 0; i < ALGORITHMS.length; i++) {
            Measurement gai = results[i][0];
            Measurement raw = results[i][1];
            Measurement aj  = results[i][2];

            String gaiV = gai == null ? "  ERROR   " : String.format("%-10s", gai.verdictStr());
            String rawV = raw == null ? "  ERROR   " : String.format("%-10s", raw.verdictStr());
            String ajV  = aj  == null ? "  N/A     " : String.format("%-10s", aj.verdictStr());

            String agree = computeAgreement(gai, raw, aj);

            System.out.printf("│ %-24s │ %-10s │ %-10s │ %-10s │ %-9s │%n",
                ALGORITHMS[i].shortName, gaiV, rawV, ajV, agree);

            if (!agree.contains("N/A") && !agree.contains("ERR")) {
                rows++;
                if (agree.startsWith("YES")) agreements++;
            }
        }

        System.out.println("├──────────────────────────┴────────────┴────────────┴────────────┴───────────┤");
        System.out.printf("│  Strategies agreed on %d / %d algorithms%-37s│%n",
            agreements, rows, "");
        System.out.println("└────────────────────────────────────────────────────────────────────────────────┘");
        System.out.println("  LIN = linearizable  |  NON = not linearizable  |  ? = runs disagreed");
        System.out.println("  AGREE = GAIsnap, RAWsnap, and AspectJ returned the same unanimous verdict");
    }

    /**
     * Returns agreement status between the three strategies.
     * "YES ✓"    — all three agree, all unanimous
     * "YES (2)"  — only two strategies available, they agree
     * "NO  !"    — strategies disagree (soundness concern)
     * "MIXED"    — at least one strategy has non-unanimous runs
     */
    static String computeAgreement(Measurement gai, Measurement raw, Measurement aj) {
        if (gai == null || raw == null) return "ERR";

        // Check if individual strategies are unanimous
        boolean gaiUnan = gai.unanimous();
        boolean rawUnan = raw.unanimous();
        boolean ajUnan  = aj == null || aj.unanimous();

        if (!gaiUnan || !rawUnan || !ajUnan) return "MIXED  ";

        // All unanimous — do they agree?
        boolean gaiLin = gai.linCount() == gai.totalRuns();
        boolean rawLin = raw.linCount() == raw.totalRuns();

        if (aj == null) {
            // Only two strategies
            return gaiLin == rawLin ? "YES (2)" : "NO  !  ";
        }

        boolean ajLin = aj.linCount() == aj.totalRuns();
        boolean allAgree = (gaiLin == rawLin) && (rawLin == ajLin);
        return allAgree ? "YES ✓  " : "NO  !  ";
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    static long ms(Measurement m) { return m == null ? -1 : m.totalMs(); }

    static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  BatchComparison: GAIsnap vs RAWsnap vs AspectJ                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf("  Threads: %d  |  Operations: %d  |  Warmup: %d  |  Measured: %d%n%n",
            THREADS, OPERATIONS, WARMUP_ROUNDS, MEASURED_ROUNDS);
        System.out.println("  Running (GAI / RAW / AJ total ms shown per algorithm) ...");
    }

    static void printFooter() {
        System.out.println();
        System.out.println("  Done.");
        System.out.printf("  Machine : %s / %d logical CPUs%n",
            System.getProperty("os.name"),
            Runtime.getRuntime().availableProcessors());
        System.out.printf("  JVM     : %s %s%n",
            System.getProperty("java.vm.name"),
            System.getProperty("java.version"));
    }
}
