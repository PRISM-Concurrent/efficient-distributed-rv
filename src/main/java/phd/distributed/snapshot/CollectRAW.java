package phd.distributed.snapshot;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import phd.distributed.config.SystemConfig;
import phd.distributed.datamodel.Event;
import phd.distributed.datamodel.OperationCall;
import phd.distributed.logging.AsyncEventLogger;
import phd.distributed.logging.DisruptorEventLogger;
import phd.distributed.logging.EventLogger;

public class CollectRAW extends Snapshot {

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
    private final IFn writeInvFn;
    private final IFn writeResFn;
    private final IFn xeForJitFn;

    // Per-thread last op-id (to reuse for the corresponding return)
    private final String[] lastOpIdPerThread;

    // Per-thread operation index (for generating unique op-ids)
    private final int[] localOpIndex;

    public CollectRAW(int numThreads) {
        // Require Clojure namespace log-rAw
        Clojure.var("clojure.core", "require").invoke(Clojure.read("logrAw"));

        // Get Clojure functions
        this.initLogsFn = Clojure.var("logrAw", "init-logs!");
        this.writeInvFn = Clojure.var("logrAw", "log-invoke!");
        this.writeResFn = Clojure.var("logrAw", "log-return!");
        this.xeForJitFn = Clojure.var("logrAw", "xe-for-jit-from-logs");


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

        // Increase the per-thread operation index
        int opIndex = ++localOpIndex[id];
        String opId = genOpId(id, opIndex);  // unique operation id for this invocation

        // Remember op-id for this thread, to reuse in the corresponding return
        lastOpIdPerThread[id] = opId;

        // Call Clojure: (log-invoke! tid op-id op arg)
        writeInvFn.invoke(
                id,                               // tid
                Keyword.intern(null, opId),       // op-id as keyword
                Keyword.intern(null, opName),     // op as keyword, e.g. :enqueue
                args                              // arg
        );

        if (ASYNC_LOGGER != null) {
            Event invEvent = new Event(id, inv, id);
            ASYNC_LOGGER.logEvent(invEvent);
        } else {
            LOGGER.info("Thread {} will write an invocation: {}({})", id, opName, arg);
        }
    }

    @Override
    public void snapshot(int id, Object resObject) {
        // Reuse the same op-id as the last invocation of this thread
        String opId = lastOpIdPerThread[id];
        String resString = objAsString(resObject);


        if (opId == null) {
            // Optional: warn if snapshot is called before any invocation
            LOGGER.warn("Thread {} snapshot called without a previous invocation op-id.", id);
            // Got to throw here
        }

        // Call Clojure: (log-return! tid op-id res)
        writeResFn.invoke(
                id,                               // tid
                Keyword.intern(null, opId),       // op-id
                resString                         // res
        );

        if (ASYNC_LOGGER != null) {
            Event resEvent = new Event(id, resObject, id);
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
        LOGGER.info("-- In build");
        return (IPersistentVector) xeForJitFn.invoke();
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