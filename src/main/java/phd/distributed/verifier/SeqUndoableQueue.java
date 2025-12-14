package phd.distributed.verifier;
import java.util.ArrayDeque;
import java.util.Deque;

public class SeqUndoableQueue implements Undoable {

    private final Deque<Object> q = new ArrayDeque<>();
    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    /** Enqueue secuencial (acepta cualquier objeto). */
    public void enqueue(Object x) {
        q.addLast(x);
        undoStack.push(() -> q.removeLast());
    }

    /** Dequeue secuencial: devuelve el primer elemento o null. */
    public Object dequeue() {
        if (!q.isEmpty()) {
            Object v = q.removeFirst();
            undoStack.push(() -> q.addFirst(v));
            return v;
        } else {
            // Registramos un paso "vacío" para poder deshacer simétricamente
            undoStack.push(() -> {});
            return null;
        }
    }

    @Override
    public void undo() {
        if (undoStack.isEmpty())
            throw new IllegalStateException("Nothing to undo");
        undoStack.pop().run();
    }

    @Override
    public String toString() {
        return "SeqUndoableQueue" + q.toString();
    }
}