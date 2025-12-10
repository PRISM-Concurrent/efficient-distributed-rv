package phd.distributed.verifier;
import java.util.function.Function;


public interface MkInvoke<S> {
    InvokeEvent<S> mkInvoke(int t, String msg, Function<S, Object> seqOp);
}
