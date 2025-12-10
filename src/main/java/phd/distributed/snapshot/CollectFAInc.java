package phd.distributed.snapshot;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import clojure.java.api.Clojure;
import clojure.lang.IPersistentVector;
import clojure.lang.IFn;
import clojure.lang.Keyword;

import phd.distributed.datamodel.OperationCall;
import phd.distributed.config.SystemConfig;
import phd.distributed.datamodel.Event;
import phd.distributed.logging.AsyncEventLogger;
import phd.distributed.logging.DisruptorEventLogger;
import phd.distributed.logging.EventLogger;

public class CollectFAInc extends Snapshot {

    private final AtomicInteger atomicCounter;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final EventLogger ASYNC_LOGGER = initLogger();

    private static EventLogger initLogger() {
        if (!SystemConfig.ASYNC_LOGGING_ENABLED) return null;
        return SystemConfig.USE_DISRUPTOR
            ? DisruptorEventLogger.getInstance()
            : AsyncEventLogger.getInstance();
    }

    // Clojure interop
    private final IFn initLogsFn;
    private final IFn writeLogFn;
    private final IFn xeForJitFn;
    private final Keyword KW_INVOKE;
    private final Keyword KW_RETURN;
    // Per-thread last op-id (to reuse for the corresponding return)
    private final String[] lastOpIdPerThread;

    // Per-thread operation index (for generating unique op-ids)
    private final int[] localOpIndex;

    public CollectFAInc(int numThreads) {
        this.atomicCounter = new AtomicInteger(0);

        // Require Clojure namespace log-tAs
        Clojure.var("clojure.core", "require").invoke(Clojure.read("logtAs"));

        // Get Clojure functions
        this.initLogsFn = Clojure.var("logtAs", "init-logs!");
        this.writeLogFn = Clojure.var("logtAs", "write-log-tAs!");
        this.xeForJitFn = Clojure.var("logtAs", "xe-for-jit");

        // Clojure keywords :invoke and :return
        this.KW_INVOKE = Keyword.intern(null, "invoke");
        this.KW_RETURN = Keyword.intern(null, "return");

        // Initialize logs-var in Clojure
        initLogsFn.invoke(numThreads);
        this.lastOpIdPerThread = new String[numThreads];
        this.localOpIndex = new int[numThreads];
    }


    private String genOpId(int tid, int index) {
        // Unique per (thread, operation index)
        return "-" + tid + "-" + index;
    }

    @Override
    public void write(int id, Object inv) {
        OperationCall call = (OperationCall) inv;
        String opName = call.method().getName();
        Object arg    = call.args();
        String args = call.argsAsString();
        int count = atomicCounter.incrementAndGet();

        // Increase the per-thread operation index
        int opIndex = ++localOpIndex[id];
        String opId = genOpId(id, opIndex);  // unique operation id for this invocation

        // Remember op-id for this thread, to reuse in the corresponding return
        lastOpIdPerThread[id] = opId;

        // Call Clojure: (write-log-tAs! :invoke tid op-id op arg count)
        writeLogFn.invoke(
                KW_INVOKE,                           // :invoke
                id,                                  // tid
                Keyword.intern(null, opId), // op-id as keyword
                Keyword.intern(null, opName),        // op as keyword, e.g. :enqueue
                args,                                 // arg
                count                                // count
        );

        if (ASYNC_LOGGER != null) {
            Event invEvent = new Event(id, inv, count);
            ASYNC_LOGGER.logEvent(invEvent);
        } else {
            LOGGER.info("Thread {} will write an invocation: {}({})", id, opName, arg);
        }


    }

    @Override
    public void snapshot(int id, Object resObject) {
        int count = atomicCounter.incrementAndGet();

        // You might later want to reuse the same op-id as the corresponding invocation.
        String opId = lastOpIdPerThread[id];
        String resString = objAsString(resObject);

        writeLogFn.invoke(
                KW_RETURN,                           // :return
                id,                                  // tid
                Keyword.intern(null, opId),          // op-id
                Keyword.intern(null, "return"),      // op = :return
                resString,                           // arg (used as :res in XE)
                count                                // count
        );

        if (ASYNC_LOGGER != null) {
            Event resEvent = new Event(id, resObject, count);
            ASYNC_LOGGER.logEvent(resEvent);
        } else {
            LOGGER.info("Thread {} will write a response: {}", id, resString);
        }
    }


    /**
     * Build the X_E history (flattened execution) in Clojure format.
     * Returns a Clojure IPersistentVector of event maps.
     */
    @Override
    public IPersistentVector buildXE() {
        return (IPersistentVector) xeForJitFn.invoke();
    }

    private String toResultString(Object resObject) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String objAsString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj.getClass().isArray()) {
            return Arrays.deepToString((Object[]) obj);
        }
        return obj.toString();
    }
}
