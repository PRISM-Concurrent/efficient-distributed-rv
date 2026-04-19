package phd.experiments.aspectj;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import phd.distributed.snapshot.Snapshot;

/**
 * Snapshot implementation for the AspectJ experiment.
 *
 * @deprecated This class is obsolete. The current AspectJ experiment
 * uses {@link NativeAspectJSnapshot} together with
 * {@link NativeTraceCollector}, which is the path exercised by
 * {@code LinearizabilityMonitorAspect} and all benchmarks.
 * {@code AspectJSnapshot} remains only for historical reference and is
 * not wired into the instrumentation pipeline.
 *
 * <p>Historical design (SWMR, same as CollectRAW):
 * <ul>
 *   <li>{@code write()} stored the logical process-id in a ThreadLocal so the
 *       aspect could later tag the intercepted call with the correct tid.</li>
 *   <li>{@code snapshot()} was a no-op: the aspect already captured the
 *       return value.</li>
 *   <li>{@code logInvoke()} / {@code logReturn()} were called BY the aspect
 *       when it intercepted {@code Method.invoke()} inside {@code A.apply()}.</li>
 *   <li>{@code buildXE()} delegated to the same {@code logrAw} Clojure
 *       namespace as CollectRAW.</li>
 * </ul>
 *
 * <p>The difference with CollectRAW was <em>where</em> {@code log-invoke!} /
 * {@code log-return!} were called: here in the AspectJ advice, not in the
 * Wrapper.
 *
 * <p>Thread-safety (SWMR): identical argument as CollectRAW — slot {@code [tid]}
 * in {@code invs-var} / {@code returns-var} is written exclusively by thread
 * {@code tid}. {@code buildXE()} is called after all producers have finished
 * ({@code awaitTermination} establishes happens-before per the Java Memory Model).
 */
@Deprecated
public class AspectJSnapshot extends Snapshot {

    // ── Clojure interop (reuses the same logrAw namespace as CollectRAW) ──
    private final IFn initLogsFn;
    private final IFn writeInvFn;
    private final IFn writeResFn;
    private final IFn xeForJitFn;

    // ── Per-thread state ───────────────────────────────────────────────────
    /** Logical process-id set by write() so the aspect can read it. */
    private final ThreadLocal<Integer> currentProcessId =
        ThreadLocal.withInitial(() -> -1);

    /** Last op-id generated for the current thread (reused in logReturn). */
    private final String[] lastOpIdPerThread;

    /** Per-thread monotonic operation counter for generating unique op-ids. */
    // Thread-safety contract: each slot [tid] in localOpIndex and
    // lastOpIdPerThread is written exclusively by the thread assigned to
    // logical process-id tid by the Executioner. The Executioner's
    // thread-per-process model means there is no concurrent write to the
    // same slot. buildXE() runs after awaitTermination, which establishes
    // the happens-before required to observe the final values.
    // This contract must hold if AspectJSnapshot is ever re-wired into the
    // instrumentation pipeline; see NativeAspectJSnapshot for the active
    // implementation.
    private final int[] localOpIndex;

    // ── Constructor ────────────────────────────────────────────────────────
    public AspectJSnapshot(int numThreads) {
        // Reuse the same Clojure namespace as CollectRAW.
        // init-logs! resets invs-var and returns-var, so each new instance
        // starts with a clean slate.
        Clojure.var("clojure.core", "require").invoke(Clojure.read("logrAw"));

        this.initLogsFn = Clojure.var("logrAw", "init-logs!");
        this.writeInvFn = Clojure.var("logrAw", "log-invoke!");
        this.writeResFn = Clojure.var("logrAw", "log-return!");
        this.xeForJitFn = Clojure.var("logrAw", "xe-for-jit-from-logs");

        initLogsFn.invoke(numThreads);

        this.lastOpIdPerThread = new String[numThreads];
        this.localOpIndex      = new int[numThreads];
    }

    // ── Snapshot API ───────────────────────────────────────────────────────

    /**
     * Called by Wrapper before execution.
     * Stores the logical process-id so the aspect can tag the intercepted call.
     */
    @Override
    public void write(int id, Object inv) {
        currentProcessId.set(id);
    }

    /**
     * Called by Wrapper after execution. No-op: the aspect already captured
     * the return value via logReturn().
     */
    @Override
    public void snapshot(int id, Object resObject) {
        // intentional no-op
    }

    /**
     * Called by the aspect BEFORE Method.invoke() executes.
     * Logs the invocation to the logrAw Clojure state.
     *
     * Format must match CollectRAW.write() exactly:
     *   tid    → int (logical process-id)
     *   op-id  → Keyword, e.g. :-0-1
     *   op     → Keyword, e.g. :offer
     *   arg    → null (Java null → Clojure nil) for no-arg methods,
     *            or String representation for methods with arguments.
     *            Mirrors OperationCall.argsAsString() / CollectRAW.write().
     */
    public void logInvoke(String methodName, Object[] actualArgs) {
        int id = currentProcessId.get();
        if (id < 0 || id >= lastOpIdPerThread.length) return;

        int opIndex = ++localOpIndex[id];
        String opId = "-" + id + "-" + opIndex;
        lastOpIdPerThread[id] = opId;

        // Mirror CollectRAW: null for no-arg, toString for single, deepToString
        // for multi-arg.  CollectRAW uses OperationCall.argsAsString() which
        // returns null (not "null") when the argument is null/absent.
        Object argVal = argsAsObject(actualArgs);

        writeInvFn.invoke(
            id,
            Keyword.intern(null, opId),
            Keyword.intern(null, methodName),
            argVal          // may be null → Clojure nil, matching CollectRAW
        );
    }

    /**
     * Called by the aspect AFTER Method.invoke() returns.
     * Logs the response to the logrAw Clojure state.
     *
     * Format must match CollectRAW.snapshot() / objAsString():
     *   null result → pass Java null (Clojure nil), NOT the string "null".
     *   Non-null    → result.toString(), same as CollectRAW.objAsString().
     */
    public void logReturn(Object result) {
        int id = currentProcessId.get();
        if (id < 0 || id >= lastOpIdPerThread.length) return;

        String opId = lastOpIdPerThread[id];
        if (opId == null) return;

        // Mirror CollectRAW.objAsString(): null stays null, arrays use
        // deepToString, everything else uses toString.
        String resStr = objAsString(result);

        writeResFn.invoke(
            id,
            Keyword.intern(null, opId),
            resStr          // null → Clojure nil, matching CollectRAW
        );
    }

    /**
     * Builds the X_E history vector for the JitLin checker.
     * Delegates to logrAw/xe-for-jit-from-logs, same as CollectRAW.
     */
    @Override
    public IPersistentVector buildXE() {
        return (IPersistentVector) xeForJitFn.invoke();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Mirrors OperationCall.argsAsString() used by CollectRAW.write():
     *   - empty / null args → null  (not the string "null")
     *   - single arg        → arg.toString()
     *   - multiple args     → Arrays.deepToString (same as argsAsString for arrays)
     */
    private static Object argsAsObject(Object[] args) {
        if (args == null || args.length == 0) return null;          // null → Clojure nil
        if (args.length == 1)                 return String.valueOf(args[0]);
        return java.util.Arrays.deepToString(args);                 // deepToString, not shallow
    }

    /**
     * Mirrors CollectRAW.objAsString():
     *   - null              → null  (not the string "null")
     *   - array             → Arrays.deepToString
     *   - everything else   → toString
     */
    private static String objAsString(Object obj) {
        if (obj == null)              return null;
        if (obj.getClass().isArray()) return java.util.Arrays.deepToString((Object[]) obj);
        return obj.toString();
    }
}
