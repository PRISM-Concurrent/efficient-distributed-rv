package phd.distributed.verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import phd.distributed.datamodel.Event;
import phd.distributed.snapshot.Snapshot;

/**
 * Adapter between Snapshot (CollectFAInc) and JITLinUndoTester.
 *
 * Takes the events in CollectFAInc and builds a history
 * with InvokeEvent/ReturnEvent to check linearizability with respect
 * to a SeqUndoableQueue.
 */
public class SnapshotJITQueueChecker {

    private static final Logger LOGGER = LogManager.getLogger(SnapshotJITQueueChecker.class);

    /**
     * Checks if the current history stored in snapshot is linearizable
     * with respect to a sequential queue specification (SeqUndoableQueue).
     *
     * @param snapshot   instance of Snapshot (e.g., CollectFAInc)
     * @param numThreads number of processes that register events
     * @param verbose    print solver details
     * @return           >0 if LIN, <=0 otherwise
     */
    public static int checkQueueHistory(Snapshot snapshot, int numThreads, boolean verbose) {
        // 1) Sequential specification + JIT solver
        SeqUndoableQueue seqQ = new SeqUndoableQueue();
        JITLinUndoTester<SeqUndoableQueue> solver =
                new JITLinUndoTester<>(seqQ, numThreads, -1L, verbose);

        // 2) Get events from snapshot, ordered by counter (stable for equal counters)
        //Set<Event> raw = snapshot.scanAll();
        Set<Event> raw = null;
        List<Event> ordered = new ArrayList<>(raw);
        ordered.sort(Comparator.comparingInt(Event::getCounter));

        // 3) For each thread: queue of pending supported ops, and count of ignored ops
        Map<Integer, Deque<InvokeEvent<SeqUndoableQueue>>> pending = new HashMap<>();
        Map<Integer, Integer> ignoredPending = new HashMap<>();
        for (int i = 0; i < numThreads; i++) {
            pending.put(i, new ArrayDeque<>());
            ignoredPending.put(i, 0);
        }

        List<phd.distributed.verifier.Event> jitEvents = new ArrayList<>();

        // 4) Translate each Event into Invoke/Return for the solver
        for (Event e : ordered) {
            int t = e.getId();         // your Event(id, value, counter)
            Object v = e.getEvent();   // invocation string "op ...", or result

            if (isInvocation(v)) {
                String msg = v.toString();
                Function<SeqUndoableQueue, Object> seqOp = buildSeqOp(msg);

                if (seqOp == null) {
                    // This operation is not part of the spec we are checking (size, peek, etc.)
                    ignoredPending.put(t, ignoredPending.get(t) + 1);
                    if (verbose) {
                        LOGGER.warn("⚠️ Ignoring unsupported operation in spec: {}", msg);
                    }
                } else {
                    // Supported operation: create InvokeEvent and mark as pending
                    InvokeEvent<SeqUndoableQueue> inv =
                            new InvokeEvent<>(t, msg, seqOp);
                    pending.get(t).addLast(inv);
                    jitEvents.add(inv);
                }
            } else {
                // This is a response event
                int ign = ignoredPending.getOrDefault(t, 0);
                if (ign > 0) {
                    // Response for a previously ignored operation
                    ignoredPending.put(t, ign - 1);
                    continue;
                }

                Deque<InvokeEvent<SeqUndoableQueue>> dq = pending.get(t);
                if (dq == null || dq.isEmpty()) {
                    if (verbose) {
                        LOGGER.warn("⚠️ Response without pending invocation (T{}, value={})", t, v);
                    }
                    continue;
                }

                InvokeEvent<SeqUndoableQueue> inv = dq.removeFirst();
                ReturnEvent ret = new ReturnEvent(t, v);
                inv.setReturnEvent(ret);
                jitEvents.add(ret);
            }
        }

        // 5) Run solver
        phd.distributed.verifier.Event[] arr =
                jitEvents.toArray(new phd.distributed.verifier.Event[0]);

        if (verbose) {
            LOGGER.info("=== History translated for JITLinUndoTester ===");
            for (phd.distributed.verifier.Event ev : arr) {
                LOGGER.info("{}", ev);
            }
        }

        int res;
        try {
            res = solver.solve(arr);
        } catch (Throwable ex) {
            LOGGER.error("Solver threw an exception: ", ex);
            res = -1;
        }

        if (res > 0) {
            LOGGER.info("✅ History is linearizable (according to SeqUndoableQueue).");
        } else {
            LOGGER.error("❌ History is NOT linearizable.");
        }

        return res;
    }

    // -------- helpers --------

    /** Determines whether a value corresponds to an invocation based on its format. */
    private static boolean isInvocation(Object v) {
        if (!(v instanceof String)) return false;
        String s = (String) v;
        return s.startsWith("op ") && s.contains("(") && s.endsWith(")");
    }

    /**
     * Maps "op ..." to a sequential operation over SeqUndoableQueue.
     * Here you decide which part of the API you want to verify.
     */
    private static Function<SeqUndoableQueue, Object> buildSeqOp(String raw) {
        if (!raw.startsWith("op ")) return null;
        String body = raw.substring(3).trim(); // e.g., "offer(obj-5)"

        int paren = body.indexOf('(');
        if (paren < 0 || !body.endsWith(")")) return null;

        String name = body.substring(0, paren).trim();                 // e.g., "offer"
        String args = body.substring(paren + 1, body.length() - 1).trim(); // e.g., "obj-5" or "null"

        switch (name) {
            case "offer": {
                // For now: treat the argument as an opaque object label.
                Object val = (args.isEmpty() || "null".equals(args)) ? null : args;
                return q -> { q.enqueue(val); return Boolean.TRUE; };
            }
            case "poll":
            case "dequeue": {
                // Specification: dequeue returns element or null
                return SeqUndoableQueue::dequeue;
            }
            default:
                // size, peek, containsAll, stream, contains, toArray, etc. → not in our spec (ignored)
                return null;
        }
    }
}
