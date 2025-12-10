package phd.distributed.verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JITLinUndoTester<S extends Undoable> {

    private static final Logger LOGGER = LogManager.getLogger(JITLinUndoTester.class);

    private final S seqObj;
    private final int p;
    private final long maxSize;
    private final boolean verbose;

    private final UndoConfig<S> config;
    private final int[] maxReachedFor;
    private final List<Object>[] allowedResults;

    private Event[] events;
    private int maxReached = 0;
    private volatile boolean interrupted = false;

    @SuppressWarnings("unchecked")
    public JITLinUndoTester(S seqObj, int p, long maxSize, boolean verbose) {
        this.seqObj = seqObj;
        this.p = p;
        this.maxSize = (maxSize <= 0) ? Long.MAX_VALUE : maxSize;
        this.verbose = verbose;

        this.config = new UndoConfig<>(seqObj, p);
        this.maxReachedFor = new int[p];
        this.allowedResults = (List<Object>[]) new List[p];
        for (int i = 0; i < p; i++) {
            allowedResults[i] = new ArrayList<>();
        }
    }

    // Factories to be used in SharedLog
    public MkInvoke<S> mkInvoke() {
        return (t, msg, op) -> new InvokeEvent<>(t, msg, op);
    }

    public MkReturn mkReturn() {
        return (t, result) -> new ReturnEvent(t, result);
    }

    // ================== Internal search stack objects ==================

    private abstract static class StackObject { }

    private static final class SolveObj extends StackObject {
        final int i;
        SolveObj(int i) { this.i = i; }
    }

    private static final class UninvokeObj<S extends Undoable> extends StackObject {
        final int t;
        final String msg;
        final Function<S, Object> op;
        final Object result;

        UninvokeObj(int t, String msg, Function<S, Object> op, Object result) {
            this.t = t;
            this.msg = msg;
            this.op = op;
            this.result = result;
        }
    }

    private static final class FireOthersThenObj extends StackObject {
        final int t;
        final int i;
        final int t1;

        FireOthersThenObj(int t, int i, int t1) {
            this.t = t;
            this.i = i;
            this.t1 = t1;
        }
    }

    private static final class UndoFireOthersObj extends StackObject {
        final int t;
        final int i;
        final int t1;
        final UndoConfig.ThreadState prev;

        UndoFireOthersObj(int t, int i, int t1, UndoConfig.ThreadState prev) {
            this.t = t;
            this.i = i;
            this.t1 = t1;
            this.prev = prev;
        }
    }

    // Next thread to try when firing
    private int next(int t, int t1) {
        if (t1 == t && t1 != 0) return 0;
        else if (t1 + 1 != t) return t1 + 1;
        else return t1 + 2;
    }

    private FireOthersThenObj nextFireEvent(int t, int i, int t1) {
        int t2 = next(t, t1);
        return (t2 < p) ? new FireOthersThenObj(t, i, t2) : null;
    }

    private void debug(Event[] es) {
        LOGGER.error("Non-linearizable history prefix detected.");
        if (maxReached >= es.length) return;
        for (int i = 0; i <= maxReached; i++) {
            LOGGER.error("  {}", es[i]);
        }
        // You may also log allowedResults[t] here if needed for diagnostics.
    }

    // ================== Main algorithm ==================

    public int solve(Event[] es) {
        this.events = es;
        Deque<StackObject> stack = new ArrayDeque<>();
        StackObject current = new SolveObj(0);
        long count = 0L;

        while (current != null || !stack.isEmpty()) {
            if (interrupted) return Solver.Interrupted;

            count++;
            if (count >= maxSize) {
                LOGGER.warn("JIT Tree Search giving up (maxSize={} reached).", maxSize);
                return Solver.OutOfSteam;
            }

            if (current == null) {
                current = stack.pop();
            }

            if (current instanceof SolveObj) {
                int i = ((SolveObj) current).i;

                // Base case
                if (i == es.length) return Solver.Success;
                if (i > maxReached) maxReached = i;

                Event ev = es[i];

                // --- Invoke event ---
                if (ev instanceof InvokeEvent) {
                    @SuppressWarnings("unchecked")
                    InvokeEvent<S> im = (InvokeEvent<S>) ev;
                    int t = im.t;
                    String msg = im.msg;
                    Function<S, Object> op = im.op;

                    if (verbose)
                        LOGGER.info("{} ,{}: T{} invokes {}", seqObj, i, t, msg);

                    if (i > maxReachedFor[t]) {
                        maxReachedFor[t] = i;
                        allowedResults[t].clear();
                    }

                    Object expected = (im.ret != null) ? im.ret.result : null;

                    // Register pending invocation
                    config.invoke(t, msg, op, expected);

                    // Next step: continue solving
                    current = new SolveObj(i + 1);
                    // Push undo of this invocation onto the stack
                    stack.push(new UninvokeObj<>(t, msg, op, expected));
                }

                // --- Return event ---
                else if (ev instanceof ReturnEvent) {
                    ReturnEvent re = (ReturnEvent) ev;
                    int t = re.t;

                    if (i > maxReachedFor[t]) maxReachedFor[t] = i;

                    if (verbose)
                        LOGGER.info("{} ,{}: T{} returns {}", seqObj, i, t, re.result);

                    // Try to fire other threads' operations around this return
                    current = new FireOthersThenObj(t, i, t);
                }

                else {
                    throw new IllegalStateException("Unknown event type: " + ev);
                }
            }

            // --- Undo an invocation ---
            else if (current instanceof UninvokeObj) {
                @SuppressWarnings("unchecked")
                UninvokeObj<S> u = (UninvokeObj<S>) current;

                if (verbose)
                    LOGGER.info("Undoing: T{} invokes {}", u.t, u.msg);

                config.uninvoke(u.t, u.msg, u.op, u.result);
                current = null; // continue with top of stack
            }

            // --- Try to fire pending operations before this return ---
            else if (current instanceof FireOthersThenObj) {
                FireOthersThenObj f = (FireOthersThenObj) current;
                int t = f.t;
                int i = f.i;
                int t1 = f.t1;

                if (t1 >= p) {
                    current = null;
                    continue;
                }

                if (config.hasPending(t1) || t == t1) {
                    UndoConfig.Either<UndoConfig.ThreadState, Object> oPrev =
                            (t == t1) ? config.fireRet(t) : config.fire(t1);

                    if (oPrev.isLeft()) {
                        UndoConfig.ThreadState prev = oPrev.left;

                        if (verbose)
                            LOGGER.info("{}: Fired T{} -> {}", i, t1, seqObj);

                        // Case 1: same thread as the return, move to next event
                        if (t1 == t) {
                            current = new SolveObj(i + 1);
                        }
                        // Case 2: different thread, keep trying with others
                        else {
                            current = new FireOthersThenObj(t, i, t);
                        }

                        // Push undo of this fire
                        stack.push(new UndoFireOthersObj(t, i, t1, prev));
                    }
                    else {
                        Object bad = oPrev.right;

                        if (verbose)
                            LOGGER.info("Failed to fire T{}", t1);

                        // Store conflicting results (Lowe-style diagnostics)
                        if (bad != null
                                && i == maxReachedFor[t1]
                                && !allowedResults[t1].contains(bad)) {
                            allowedResults[t1].add(bad);
                        }

                        // If it's the same thread as return and we can now return, stop here
                        if (t == t1 && config.canReturn(t)) {
                            current = null;
                        } else {
                            current = nextFireEvent(t, i, t1);
                        }
                    }
                } else {
                    // No pending op there; try next thread
                    current = nextFireEvent(t, i, t1);
                }
            }

            // --- Undo a previous fire (backtracking step) ---
            else if (current instanceof UndoFireOthersObj) {
                UndoFireOthersObj u = (UndoFireOthersObj) current;

                if (verbose)
                    LOGGER.info("{}: Undoing: T{} firing", u.i, u.t1);

                config.undo(u.t1, u.prev);
                current = nextFireEvent(u.t, u.i, u.t1);
            }
        }

        // If we exit the loop without success, it's a failure
        debug(es);
        return Solver.Failure;
    }
}
