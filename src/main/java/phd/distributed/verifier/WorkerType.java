package phd.distributed.verifier;

@FunctionalInterface
public interface WorkerType<S, C> {
    void run(int me, GenericThreadLog<S, C> log);
}
