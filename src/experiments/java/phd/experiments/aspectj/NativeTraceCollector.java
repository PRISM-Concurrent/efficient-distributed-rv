package phd.experiments.aspectj;

import clojure.lang.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Trace collector following the El-Hokayem & Falcone style.
 * * This class provides a high-performance mechanism to capture execution traces 
 * by using thread-local buffers. This approach minimizes synchronization 
 * overhead during the execution phase of distributed algorithms.
 * * Note: Since events are collected in local buffers and merged/sorted later 
 * based on {@link System#nanoTime()}, the resulting trace may not perfectly 
 * reflect the real-time order if events occur within the precision 
 * error margin of the clock.
 */
public class NativeTraceCollector {

    /** Global queue holding references to all thread-local buffers for final merging. */
    private static final ConcurrentLinkedQueue<List<TraceEvent>> allBuffers =
            new ConcurrentLinkedQueue<>();

    /** Thread-local storage for events to avoid contention between threads. */
    private static final ThreadLocal<List<TraceEvent>> localBuffer =
            ThreadLocal.withInitial(() -> {
                List<TraceEvent> buffer = new ArrayList<>(5000);
                allBuffers.offer(buffer);
                return buffer;
            });

    /** Counter per thread to generate unique operation IDs within the thread's scope. */
    private static final ThreadLocal<Integer> localOpIndex = ThreadLocal.withInitial(() -> 0);

    /**
     * Internal representation of a trace event (Invoke or Return).
     */
    private static class TraceEvent {
        final long   nanoTime;
        final boolean isInvoke;
        final int    tid;
        final String opId;
        final String methodName;
        final Object argOrResult;

        TraceEvent(long nanoTime, boolean isInvoke, int tid,
                   String opId, String methodName, Object argOrResult) {
            this.nanoTime    = nanoTime;
            this.isInvoke    = isInvoke;
            this.tid         = tid;
            this.opId        = opId;
            this.methodName  = methodName;
            this.argOrResult = argOrResult;
        }
    }

    /**
     * Records an operation by creating both an 'invoke' and a 'return' event.
     * Both events are stored together using the same capture timestamp base.
     * * @param tid The thread identifier.
     * @param methodName The name of the method being monitored.
     * @param arg The input argument of the operation.
     * @param result The resulting value of the operation.
     */
    public static void logOperation(int tid, String methodName, Object arg, Object result) {
        int index = localOpIndex.get() + 1;
        localOpIndex.set(index);

        String opId = "-" + tid + "-" + index;

        // Store both events together using the same capture timestamp
        long t = System.nanoTime();

        localBuffer.get().add(new TraceEvent(t,     true,  tid, opId, methodName, arg));
        localBuffer.get().add(new TraceEvent(t + 1, false, tid, opId, methodName, result));
    }

    /**
     * Resets the collector state, clearing all buffers and thread-local data.
     */
    public static void clear() {
        allBuffers.clear();
        localBuffer.remove();
        localOpIndex.remove();
    }

    /**
     * Merges all thread-local buffers into a single global trace, sorts them 
     * by timestamp, and converts the result into a Clojure-compatible 
     * PersistentVector for the Verifier.
     * * @return A Clojure IPersistentVector containing the ordered trace events.
     */
    public static IPersistentVector buildPersistentVector() {
        List<TraceEvent> globalTrace = new ArrayList<>();
        for (List<TraceEvent> buffer : allBuffers) {
            globalTrace.addAll(buffer);
        }

        // This sorting step is where event reordering potentially occurs:
        // if two threads captured nanoTime in overlapping windows, 
        // the order here might not reflect the actual execution order.
        globalTrace.sort(Comparator.comparingLong(e -> e.nanoTime));

        IPersistentVector clojureVector = PersistentVector.EMPTY;

        // Clojure Keywords for map keys
        Keyword kwType   = Keyword.intern(null, "type");
        Keyword kwOpId   = Keyword.intern(null, "op-id");
        Keyword kwTid    = Keyword.intern(null, "tid");
        Keyword kwOp     = Keyword.intern(null, "op");
        Keyword kwArg    = Keyword.intern(null, "arg");
        Keyword kwRes    = Keyword.intern(null, "res");
        Keyword kwInvoke = Keyword.intern(null, "invoke");
        Keyword kwReturn = Keyword.intern(null, "return");

        for (TraceEvent e : globalTrace) {
            Map<Object, Object> eventMap = new HashMap<>();
            eventMap.put(kwType,  e.isInvoke ? kwInvoke : kwReturn);
            eventMap.put(kwOpId,  Keyword.intern(null, e.opId));
            eventMap.put(kwTid,   e.tid);
            eventMap.put(kwOp,    Keyword.intern(null, e.methodName));

            if (e.isInvoke) {
                eventMap.put(kwArg, e.argOrResult);
            } else {
                eventMap.put(kwRes, e.argOrResult);
            }

            clojureVector = clojureVector.cons(PersistentArrayMap.create(eventMap));
        }

        return clojureVector;
    }
}