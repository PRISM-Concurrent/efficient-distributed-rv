package phd.distributed.verifier;
import java.util.Objects;
import java.util.function.Function;

import phd.distributed.verifier.UndoConfig.ThreadState;




public class UndoConfig<S extends Undoable> {

    public static abstract class ThreadState { }

    public static final class Pending extends ThreadState {
        final String msg;
        final Function<Object, Object> op;
        final Object expected;

        Pending(String msg, Function<Object, Object> op, Object expected) {
            this.msg = msg;
            this.op = op;
            this.expected = expected;
        }
    }

    public static final class Ret extends ThreadState {
        final Object res;
        Ret(Object res) { this.res = res; }
    }

    public static final class Out extends ThreadState { }

    private final S seqObj;
    private final ThreadState[] tStates;
    private final Out out = new Out();

    public UndoConfig(S seqObj, int p) {
        this.seqObj = seqObj;
        this.tStates = new ThreadState[p];
        for (int i = 0; i < p; i++) tStates[i] = out;
    }

    public void invoke(int t, String msg, Function<S, Object> op, Object result) {
        if (tStates[t] != out)
            throw new IllegalStateException("Thread " + t + " already in op");

        // guardamos como Function<Object,Object> pero realmente es Function<S,Object>
        Function<Object, Object> f = o -> op.apply((S) o);
        tStates[t] = new Pending(msg, f, result);
    }

    public void uninvoke(int t, String msg, Function<S, Object> op, Object result) {
        ThreadState st = tStates[t];
        if (!(st instanceof Pending))
            throw new IllegalStateException("No pending op to uninvoke");
        Pending p = (Pending) st;
        if (!p.msg.equals(msg) || !Objects.equals(p.expected, result))
            throw new IllegalStateException("Mismatching pending op");
        tStates[t] = out;
    }

    public static final class Either<L, R> {
        public final L left;
        public final R right;
        private Either(L l, R r) { left = l; right = r; }
        public static <L, R> Either<L, R> left(L l) { return new Either<>(l, null); }
        public static <L, R> Either<L, R> right(R r) { return new Either<>(null, r); }
        public boolean isLeft() { return left != null; }
        public boolean isRight() { return right != null; }
    }

    public Either<ThreadState, Object> fire(int t) {
        Pending pe = (Pending) tStates[t];
        Object result = pe.op.apply(seqObj);
        if (Objects.equals(pe.expected, result)) {
            tStates[t] = new Ret(result);
            return Either.left(pe);
        } else {
            seqObj.undo();
            return Either.right(result);
        }
    }

    public Either<ThreadState, Object> fireRet(int t) {
        ThreadState e = tStates[t];
        if (e instanceof Ret) {
            tStates[t] = out;
            return Either.left(e);
        } else if (e instanceof Pending) {
            Pending pe = (Pending) e;
            Object result = pe.op.apply(seqObj);
            if (Objects.equals(pe.expected, result)) {
                tStates[t] = out;
                return Either.left(e);
            } else {
                seqObj.undo();
                return Either.right(result);
            }
        } else {
            return Either.right(null);
        }
    }

    public boolean hasPending(int t) {
        return tStates[t] instanceof Pending;
    }

    public boolean canReturn(int t) {
        return tStates[t] instanceof Ret;
    }

    public ThreadState doReturn(int t) {
        ThreadState prev = tStates[t];
        tStates[t] = out;
        return prev;
    }

    public void undo(int t, ThreadState prev) {
        ThreadState cur = tStates[t];
        if (cur instanceof Ret || cur == out) {
            if (prev instanceof Pending) {
                seqObj.undo();
            }
            tStates[t] = prev;
        } else {
            throw new IllegalStateException("Invalid undo state for t=" + t);
        }
    }
}
