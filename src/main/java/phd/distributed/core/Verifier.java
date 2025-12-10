package phd.distributed.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import phd.distributed.snapshot.Snapshot;

public class Verifier {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Snapshot c;

    public Verifier(Snapshot snapshot) {
        this.c = snapshot;
    }


    public boolean checkLinearizabilityJitLin(String objectType) {


        // Construir X_E desde el snapshot (CollectFAInc o CollectRAW)
        IPersistentVector xe = this.c.buildXE();

        // === Log X_E ===
        LOGGER.info("==== X_E history ({} events) ====", xe.count());
        for (ISeq s = xe.seq(); s != null; s = s.next()) {
            Object ev = s.first();
            LOGGER.info("X_E event: {}", ev);
        }

        boolean ok = JitLinChecker.checkLinearizable(xe, LOGGER,objectType);

        if (ok) {
            LOGGER.info("\n History is LINEARIZABLE (JitLin checker).");
        } else {
            LOGGER.error("\n History is NOT linearizable (JitLin checker).");
        }
        return ok;
    }



}
