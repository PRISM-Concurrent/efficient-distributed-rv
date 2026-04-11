package phd.distributed.verifier;

import java.util.LinkedList;

/**
 * Queue con sincronización parcial — race condition sutil.
 * offer() está sincronizado pero poll() no, lo que permite
 * que poll() lea estado inconsistente bajo alta concurrencia.
 */
public class PartialSyncQueue {

    private final LinkedList<Object> list = new LinkedList<>();

    public synchronized boolean offer(Object o) {
        return list.add(o);
    }

    // Sin synchronized — race condition entre isEmpty() y removeFirst()
    public Object poll() {
        if (list.isEmpty()) return null;
        try {
            return list.removeFirst();
        } catch (java.util.NoSuchElementException e) {
            // otro thread tomó el elemento entre isEmpty() y removeFirst()
            return null;
        }
    }
}