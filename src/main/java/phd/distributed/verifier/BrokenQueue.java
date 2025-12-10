package phd.distributed.verifier;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Deliberately broken queue that violates linearizability.
 * This queue sometimes returns incorrect results to test the verification system.
 */
public class BrokenQueue<T> {
    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    private int operationCount = 0;

    /**
     * Offer operation - sometimes fails incorrectly.
     */
    public boolean offer(T item) {
        operationCount++;
        // Every 5th operation incorrectly returns false even though it succeeds
        boolean result = queue.offer(item);
        if (operationCount % 5 == 0) {
            return false;  // Lie about the result - breaks linearizability!
        }
        return result;
    }

    /**
     * Poll operation - sometimes returns wrong element.
     */
    public T poll() {
        operationCount++;
        T result = queue.poll();
        // Every 7th operation returns null even if queue has elements
        if (operationCount % 7 == 0 && result != null) {
            queue.offer(result);  // Put it back
            return null;  // But claim it was empty - breaks linearizability!
        }
        return result;
    }

    /**
     * Peek operation.
     */
    public T peek() {
        return queue.peek();
    }

    /**
     * Size operation.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
