package phd.distributed.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import phd.distributed.api.DistAlgorithm;
import phd.distributed.datamodel.OperationCall;
import phd.distributed.snapshot.Snapshot;


public class Wrapper {

    private final Snapshot c;
    private final DistAlgorithm alg;
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private static final Logger LOGGER = LogManager.getLogger();

    public Wrapper(DistAlgorithm alg, Snapshot snapshot) {
        this.alg = alg;
        this.c = snapshot;
    }

    /**
     * Executes an operation selected for the given process ID.
     * <p>
     * An operation is chosen nondeterministically using
     * {@link OperationCall#chooseOp},
     *
     * applied to the distributed algorithm via {@link DistAlgorithm#apply},
     *
     *
     * @param processId the unique identifier of the process performing the
     * operation
     */
    public void execute(int processId, OperationCall call) {
        Object result = null;
        LOGGER.info("Thread {} is  going to execute the write", processId, call.method(), call.args());
        this.c.write(processId, call);
        try {
            Object args = call.args();
            if (args instanceof Object[]) {
                result = this.alg.apply(call.method(), (Object[]) args);
            } else if (args == null) {
                result = this.alg.apply(call.method());
            } else {
                result = this.alg.apply(call.method(), args);
            }
            LOGGER.info("{}Thread {} execute {} and obtained {}{}", GREEN, processId, call.method().getName(), result, RESET);
            //System.out.println(GREEN +"Thread " + processId + " execute: " + call.method().getName() + " and obtained" + result + RESET);
        } catch (Exception e) {
            LOGGER.error("{}Thread {} failed with: {} - {}{}", RED, processId, e.getClass().getSimpleName(), e.getMessage(), RESET, e);
            //System.err.println(RED+"Thread " + processId + " failed with: " + e.getClass().getSimpleName() + " - " + e.getMessage() + RESET);
            //e.printStackTrace();
        }
        this.c.snapshot(processId, result);
        LOGGER.info("Thread {} end the wrapper", processId);
    }
}
