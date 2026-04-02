package phd.experiments.aspectj;

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.IPersistentVector;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NativeTraceCollector {

    private static final ConcurrentLinkedQueue<List<TraceEvent>> allBuffers = new ConcurrentLinkedQueue<>();

    private static final ThreadLocal<List<TraceEvent>> localBuffer = ThreadLocal.withInitial(() -> {
        List<TraceEvent> buffer = new ArrayList<>(5000);
        allBuffers.offer(buffer);
        return buffer;
    });

    private static final ThreadLocal<Integer> localOpIndex = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> currentOpId = new ThreadLocal<>();
    
    // 🔥 EL FIX ESTÁ AQUÍ: Necesitamos recordar de qué método venimos
    private static final ThreadLocal<String> currentMethodName = new ThreadLocal<>();

    private static class TraceEvent {
        final long nanoTime;
        final boolean isInvoke;
        final int tid;
        final String opId;
        final String methodName;
        final Object argOrResult;

        TraceEvent(long nanoTime, boolean isInvoke, int tid, String opId, String methodName, Object argOrResult) {
            this.nanoTime = nanoTime;
            this.isInvoke = isInvoke;
            this.tid = tid;
            this.opId = opId;
            this.methodName = methodName;
            this.argOrResult = argOrResult;
        }
    }

    // 1. Cambia el tercer parámetro a Object
    public static void logInvoke(int tid, String methodName, Object arg) {
        int index = localOpIndex.get() + 1;
        localOpIndex.set(index);
        
        String opId = "-" + tid + "-" + index;
        currentOpId.set(opId);
        currentMethodName.set(methodName);

        // Pasamos el arg crudo
        localBuffer.get().add(new TraceEvent(
            System.nanoTime(), true, tid, opId, methodName, arg
        ));
    }

    // 2. Quitamos la conversión a String de result
    public static void logReturn(int tid, Object result) {
        String opId = currentOpId.get();
        String methodName = currentMethodName.get(); 
        
        if (opId == null || methodName == null) return;

        // Pasamos el result crudo tal cual sale de Java (ej. el Boolean true)
        localBuffer.get().add(new TraceEvent(
            System.nanoTime(), false, tid, opId, methodName, result
        ));
    }

    public static void clear() {
        allBuffers.clear();
        localOpIndex.remove();
        currentOpId.remove();
        currentMethodName.remove();
    }

    public static IPersistentVector buildPersistentVector() {
        List<TraceEvent> globalTrace = new ArrayList<>();
        for (List<TraceEvent> buffer : allBuffers) {
            globalTrace.addAll(buffer);
        }

        globalTrace.sort(Comparator.comparingLong(e -> e.nanoTime));

        IPersistentVector clojureVector = PersistentVector.EMPTY;
        
        Keyword kwType   = Keyword.intern(null, "type");
        Keyword kwOpId   = Keyword.intern(null, "op-id");
        Keyword kwTid    = Keyword.intern(null, "tid");
        Keyword kwOp     = Keyword.intern(null, "op");
        Keyword kwArg    = Keyword.intern(null, "arg");
        Keyword kwRes    = Keyword.intern(null, "res"); // 🔥 NUEVA LLAVE PARA EL RETORNO
        Keyword kwInvoke = Keyword.intern(null, "invoke");
        Keyword kwReturn = Keyword.intern(null, "return");

        for (TraceEvent e : globalTrace) {
            Map<Object, Object> eventMap = new HashMap<>();
            eventMap.put(kwType, e.isInvoke ? kwInvoke : kwReturn);
            eventMap.put(kwOpId, Keyword.intern(null, e.opId));
            eventMap.put(kwTid, e.tid);
            
            // Si JitLin se vuelve a quejar, podríamos necesitar que los retornos
            // tengan la operación ":return" en vez del nombre del método, pero
            // por ahora le pasamos el nombre real (offer/poll) que es el estándar.
            eventMap.put(kwOp, Keyword.intern(null, e.methodName)); 

            // 🔥 LA CORRECCIÓN MAESTRA:
            if (e.isInvoke) {
                // Las invocaciones usan :arg
                eventMap.put(kwArg, e.argOrResult); 
            } else {
                // Los retornos usan :res
                eventMap.put(kwRes, e.argOrResult); 
            }

            clojureVector = clojureVector.cons(PersistentArrayMap.create(eventMap));
        }

        return clojureVector;
    }
}