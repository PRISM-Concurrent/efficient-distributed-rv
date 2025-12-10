package phd.distributed.verifier;
import java.util.function.Function;


public class InvokeEvent<S> implements Event {
    public final int t;
    public final String msg;
    public final Function<S, Object> op;
    public ReturnEvent ret;

    public InvokeEvent(int t, String msg, Function<S, Object> op) {
        this.t = t;
        this.msg = msg;
        this.op = op;
    }

    public void setReturnEvent(ReturnEvent ret) {
        this.ret = ret;
    }

    @Override
    public String toString() {
        return "Invoke(t=" + t + ", msg=" + msg + ")";
    }
}