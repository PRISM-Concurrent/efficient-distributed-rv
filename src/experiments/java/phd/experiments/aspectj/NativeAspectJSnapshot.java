package phd.experiments.aspectj;

import clojure.lang.IPersistentVector;
import phd.distributed.snapshot.Snapshot;

public class NativeAspectJSnapshot extends Snapshot {

    // Guarda el ID lógico (0, 1, 2...) del hilo actual
    private static final ThreadLocal<Integer> currentLogicalTid = ThreadLocal.withInitial(() -> -1);

    public NativeAspectJSnapshot() {
        // Limpiamos los buffers nativos al crear un nuevo experimento
        NativeTraceCollector.clear();
    }

    public static int getCurrentLogicalTid() {
        return currentLogicalTid.get();
    }

    @Override
    public void write(int id, Object inv) {
        // El Wrapper nos llama justo antes de la ejecución real.
        // Guardamos el TID lógico para que el aspecto pueda leerlo.
        currentLogicalTid.set(id);
    }

    @Override
    public void snapshot(int id, Object resObject) {
        // No-op. AspectJ ya se encarga de interceptar el retorno.
    }

    @Override
    public IPersistentVector buildXE() {
        // Delegamos la recolección final a nuestro motor nativo ultrarrápido
        return NativeTraceCollector.buildPersistentVector();
    }
}
