package phd.distributed.verifier;

public class ReturnEvent implements Event {
    public final int t;
    public final Object result;

    public ReturnEvent(int t, Object result) {
        this.t = t;
        this.result = result;
    }

    @Override
    public String toString() {
        return "Return(t=" + t + ", result=" + result + ")";
    }
}
