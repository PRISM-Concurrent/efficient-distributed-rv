package phd.distributed.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class SharedLog<S, C> {

    private final List<Event> events =
            Collections.synchronizedList(new ArrayList<>());

    private final C concObj;
    private final MkInvoke<S> mkInvoke;
    private final MkReturn mkReturn;

    public SharedLog(int invocs,
                     C concObj,
                     MkInvoke<S> mkInvoke,
                     MkReturn mkReturn) {
        this.concObj = concObj;
        this.mkInvoke = mkInvoke;
        this.mkReturn = mkReturn;
    }

    private void add(Event e) {
        events.add(e);
    }

    public class SharedThreadLog extends GenericThreadLog<S, C> {
        private final int t;

        public SharedThreadLog(int t) {
            this.t = t;
        }

        @Override
        public <A, B> void log(Function<C, A> concOp,
                               String msg,
                               Function<S, B> seqOp) {
            // 1) invocation
            InvokeEvent<S> inv =
                    mkInvoke.mkInvoke(t, msg, (S s) -> seqOp.apply(s));
            add(inv);

            // 2) execute concurrent operation (in scripts, only returns expected value)
            A result = concOp.apply(concObj);

            // 3) return
            ReturnEvent ret = mkReturn.mkReturn(t, result);
            add(ret);

            inv.setReturnEvent(ret);
        }
    }

    /** Get thread t log */
    public GenericThreadLog<S, C> threadLog(int t) {
        return new SharedThreadLog(t);
    }

    /** Get the full log */
    public Event[] getLog() {
        synchronized (events) {
            return events.toArray(new Event[0]);
        }
    }
}
