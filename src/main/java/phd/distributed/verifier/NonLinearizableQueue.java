package phd.distributed.verifier;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A queue that violates linearizability by reordering elements.
 * This queue will definitely fail linearizability checks.
 */
public class NonLinearizableQueue<T> {
    private final ConcurrentLinkedQueue<T> queue1 = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<T> queue2 = new ConcurrentLinkedQueue<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Offer - alternates between two queues, breaking FIFO order.
     */
    public boolean offer(T item) {
        // Alternate between two queues - this breaks linearizability
        // because poll() will return from wrong queue
        if (counter.getAndIncrement() % 2 == 0) {
            return queue1.offer(item);
        } else {
            return queue2.offer(item);
        }
    }

    /**
     * Poll - always polls from queue1 first, then queue2.
     * This violates FIFO order when items are in both queues.
     */
    public T poll() {
        T result = queue1.poll();
        if (result == null) {
            result = queue2.poll();
        }
        return result;
    }

    public T peek() {
        T result = queue1.peek();
        if (result == null) {
            result = queue2.peek();
        }
        return result;
    }

    public boolean isEmpty() {
        return queue1.isEmpty() && queue2.isEmpty();
    }

    public int size() {
        return queue1.size() + queue2.size();
    }
}
