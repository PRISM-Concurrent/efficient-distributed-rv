package phd.experiments.aspectj;

import clojure.lang.IPersistentVector;
import phd.distributed.snapshot.Snapshot;

/**
 * NativeAspectJSnapshot acts as an integration layer between the generic 
 * snapshot interface and the AspectJ-based tracing mechanism.
 * * It manages logical thread identifiers (TIDs) using ThreadLocal storage 
 * to ensure that the AspectJ aspects can correctly associate captured 
 * events with their respective threads during concurrent execution.
 */
public class NativeAspectJSnapshot extends Snapshot {

    /** Stores the logical thread ID (0, 1, 2...) of the current thread. */
    private static final ThreadLocal<Integer> currentLogicalTid = ThreadLocal.withInitial(() -> -1);

    public NativeAspectJSnapshot() {
        // Clear native buffers when initializing a new experiment to ensure trace isolation.
        NativeTraceCollector.clear();
    }

    /**
     * Retrieves the logical TID associated with the calling thread.
     * @return The logical thread ID, or -1 if not set.
     */
    public static int getCurrentLogicalTid() {
        return currentLogicalTid.get();
    }

    @Override
    public void write(int id, Object inv) {
        // The Wrapper calls this method immediately before the actual execution.
        // We store the logical TID so the aspect can read it and tag the trace events.
        currentLogicalTid.set(id);
    }

    @Override
    public void snapshot(int id, Object resObject) {
        // No-op. AspectJ is already responsible for intercepting the return values 
        // through its own pointcuts.
    }

    @Override
    public IPersistentVector buildXE() {
        // Delegate the final trace construction to the high-performance native engine.
        return NativeTraceCollector.buildPersistentVector();
    }
}