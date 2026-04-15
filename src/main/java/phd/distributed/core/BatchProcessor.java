package phd.distributed.core;

import phd.distributed.config.SystemConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class BatchProcessor<T> {
    private final int batchSize;
    private final List<T> currentBatch;
    private final Consumer<List<T>> batchHandler;
    private final ReentrantLock lock = new ReentrantLock();

    public BatchProcessor(Consumer<List<T>> batchHandler) {
        this(SystemConfig.DEFAULT_BATCH_SIZE, batchHandler);
    }

    public BatchProcessor(int batchSize, Consumer<List<T>> batchHandler) {
        this.batchSize = batchSize;
        this.currentBatch = new ArrayList<>(batchSize);
        this.batchHandler = batchHandler;
    }

    public void add(T item) {
        lock.lock();
        try {
            currentBatch.add(item);
            if (currentBatch.size() >= batchSize) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();
        try {
            if (!currentBatch.isEmpty()) {
                batchHandler.accept(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        } finally {
            lock.unlock();
        }
    }
}
