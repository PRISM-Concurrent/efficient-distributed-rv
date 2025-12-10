package phd.distributed.core;

import org.apache.logging.log4j.Logger;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentVector;

public class JitLinChecker {

    private static final IFn linFn;
    private static final IFn keywordFn;

    static {
        // 1) Cargar el namespace typelin
        Clojure.var("clojure.core", "require").invoke(Clojure.read("typelin"));

        // 2) Obtener la función typelin/linearizable?
        linFn = Clojure.var("typelin", "linearizable?");
        keywordFn = Clojure.var("clojure.core", "keyword");
    }

    /**
     * Check if the given history X_E is linearizable w.r.t. a sequential queue.
     * @param xe Clojure vector of events (output of xe-for-jit)
     * @return true if linearizable, false otherwise.
     */
    public static boolean checkLinearizable(IPersistentVector xe, Logger LOGGER, String objectType) {

        // Debug antes de llamar a Clojure
        LOGGER.info("[JitLinChecker] About to call typelin/linearizable? with {} events against {}",
                    xe.count(), objectType);

        Object specType = keywordFn.invoke(objectType); // => :queue

        Object ret;
        try {
            ret = linFn.invoke(specType, xe);
        } catch (Throwable t) {
            LOGGER.error("[JitLinChecker] Error calling typelin/linearizable?", t);
            return false;
        }

        Boolean result = (ret instanceof Boolean) ? (Boolean) ret : null;

        if (Boolean.TRUE.equals(result)) {
            LOGGER.info("[JitLinChecker] History *is* LINEARIZABLE with respect to {} ", objectType);
        } else {
            LOGGER.error("[JitLinChecker] History is NOT linearizable with respect to {} ", objectType);
        }

        return Boolean.TRUE.equals(result);
    }
}