package phd.experiments.aspectj;

import clojure.lang.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Colector de traza estilo El-Hokayem & Falcone.
 *
 * La diferencia estructural respecto al colector con @Around:
 * aquí logInvoke y logReturn son llamadas independientes que pueden
 * intercalarse entre threads. Con @Around, el return siempre seguía
 * inmediatamente al invoke del mismo thread dentro del bloque de control.
 * Aquí no hay ninguna garantía de eso.
 *
 * Consecuencia: el sort por nanoTime al final puede producir una
 * secuencia diferente a la real — exactamente el fenómeno de la Tabla
 * de la slide 12 del paper (50%+ de trazas con orden distinto).
 */
public class NativeTraceCollector {

    private static final ConcurrentLinkedQueue<List<TraceEvent>> allBuffers =
            new ConcurrentLinkedQueue<>();

    private static final ThreadLocal<List<TraceEvent>> localBuffer =
            ThreadLocal.withInitial(() -> {
                List<TraceEvent> buffer = new ArrayList<>(5000);
                allBuffers.offer(buffer);
                return buffer;
            });

    private static final ThreadLocal<Integer> localOpIndex = ThreadLocal.withInitial(() -> 0);

    // Estos dos ThreadLocals son el puente entre @Before y @After.
    // Con @Around no eran necesarios porque todo vivía en el mismo bloque.
    private static final ThreadLocal<String>  currentOpId     = new ThreadLocal<>();
    private static final ThreadLocal<String>  currentMethodName = new ThreadLocal<>();

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

    public static void logOperation(int tid, String methodName, Object arg, Object result) {
    int index = localOpIndex.get() + 1;
    localOpIndex.set(index);

    String opId = "-" + tid + "-" + index;

    // Guardamos los dos eventos juntos, con el mismo timestamp de captura
    long t = System.nanoTime();

    localBuffer.get().add(new TraceEvent(t,     true,  tid, opId, methodName, arg));
    localBuffer.get().add(new TraceEvent(t + 1, false, tid, opId, methodName, result));
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

        // Este sort es el paso que potencialmente reordena eventos:
        // si dos threads tomaron nanoTime en ventanas superpuestas,
        // el orden aquí puede no reflejar el orden de ejecución real.
        globalTrace.sort(Comparator.comparingLong(e -> e.nanoTime));

        IPersistentVector clojureVector = PersistentVector.EMPTY;

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