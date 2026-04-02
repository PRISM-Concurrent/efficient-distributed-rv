package phd.experiments.aspectj;

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.IPersistentVector;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NativeTraceCollectorPostOp {

    // Una sola cola global: el orden de inserción es el orden de llegada
    private static final ConcurrentLinkedQueue<TraceEvent> globalQueue = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<Integer> localOpIndex = ThreadLocal.withInitial(() -> 0);

    private static class TraceEvent {
        final boolean isInvoke;
        final int tid;
        final String opId;
        final String methodName;
        final Object data;

        TraceEvent(boolean isInvoke, int tid, String opId, String methodName, Object data) {
            this.isInvoke = isInvoke;
            this.tid = tid;
            this.opId = opId;
            this.methodName = methodName;
            this.data = data;
        }
    }

    public static long markStart() {
        return System.nanoTime();
    }

    /**
     * Al usar globalQueue.add(), los eventos se forman "en la fila" 
     * según van terminando las operaciones.
     */
    public static void logCompleteOperation(int tid, String methodName, Object arg, Object result, long startNano) {
        int index = localOpIndex.get() + 1;
        localOpIndex.set(index);
        
        String opId = "-" + tid + "-" + index;

        // Insertamos la pareja de golpe. Como ConcurrentLinkedQueue es thread-safe,
        // garantizamos que el invoke de esta op vaya justo antes que su return.
        globalQueue.add(new TraceEvent(true, tid, opId, methodName, arg));
        globalQueue.add(new TraceEvent(false, tid, opId, methodName, result));
    }

    public static void clear() {
        globalQueue.clear();
        localOpIndex.remove();
    }

    public static IPersistentVector buildPersistentVector() {
        IPersistentVector clojureVector = PersistentVector.EMPTY;
        
        Keyword kwType   = Keyword.intern(null, "type");
        Keyword kwOpId   = Keyword.intern(null, "op-id");
        Keyword kwTid    = Keyword.intern(null, "tid");
        Keyword kwOp     = Keyword.intern(null, "op");
        Keyword kwArg    = Keyword.intern(null, "arg");
        Keyword kwRes    = Keyword.intern(null, "res");
        Keyword kwInvoke = Keyword.intern(null, "invoke");
        Keyword kwReturn = Keyword.intern(null, "return");

        // Ya no hay sort. Iteramos la cola tal cual se llenó.
        for (TraceEvent e : globalQueue) {
            Map<Object, Object> eventMap = new HashMap<>();
            eventMap.put(kwType, e.isInvoke ? kwInvoke : kwReturn);
            eventMap.put(kwOpId, Keyword.intern(null, e.opId));
            eventMap.put(kwTid, e.tid);
            eventMap.put(kwOp, Keyword.intern(null, e.methodName)); 

            if (e.isInvoke) {
                eventMap.put(kwArg, e.data); 
            } else {
                eventMap.put(kwRes, e.data); 
            }

            clojureVector = clojureVector.cons(PersistentArrayMap.create(eventMap));
        }

        return clojureVector;
    }
}