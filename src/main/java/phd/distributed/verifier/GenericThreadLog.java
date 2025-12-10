package phd.distributed.verifier;
import java.util.function.Function;


public abstract class GenericThreadLog<S, C> {

    public abstract <A, B> void log(Function<C, A> concOp,
                                    String msg,
                                    Function<S, B> seqOp);

    public final <A, B> void apply(Function<C, A> concOp,
                                   String msg,
                                   Function<S, B> seqOp) {
        log(concOp, msg, seqOp);
    }
}