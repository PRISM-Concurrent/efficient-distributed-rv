package phd.distributed.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import phd.distributed.datamodel.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncEventLogger implements EventLogger {
    private static final Logger EVENT_LOGGER = LogManager.getLogger("phd.distributed.events");
    private static final int BUFFER_SIZE = 8192;
    private static final int BATCH_SIZE = 100;

    private final BlockingQueue<Event> eventQueue;
    private final Thread processorThread;
    private final AtomicBoolean running;

    private static final AsyncEventLogger INSTANCE = new AsyncEventLogger();

    private AsyncEventLogger() {
        this.eventQueue = new ArrayBlockingQueue<>(BUFFER_SIZE);
        this.running = new AtomicBoolean(true);
        this.processorThread = new Thread(this::processEvents, "AsyncEventLogger");
        this.processorThread.setDaemon(true);
        this.processorThread.start();
    }

    public static AsyncEventLogger getInstance() {
        return INSTANCE;
    }

    public void logEvent(Event event) {
        if (!eventQueue.offer(event)) {
            // Queue full, log synchronously as fallback
            EVENT_LOGGER.warn("Event queue full, logging synchronously: {}", event);
        }
    }

    private void processEvents() {
        List<Event> batch = new ArrayList<>(BATCH_SIZE);

        while (running.get() || !eventQueue.isEmpty()) {
            try {
                Event event = eventQueue.poll();
                if (event != null) {
                    batch.add(event);

                    // Drain more events if available
                    eventQueue.drainTo(batch, BATCH_SIZE - batch.size());

                    if (!batch.isEmpty()) {
                        processBatch(batch);
                        batch.clear();
                    }
                } else {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processBatch(List<Event> events) {
        StringBuilder sb = new StringBuilder(events.size() * 100);
        for (Event event : events) {
            sb.append(event.toString()).append('\n');
        }
        EVENT_LOGGER.info("Batch[{}]: {}", events.size(), sb.toString());
    }

    public void shutdown() {
        running.set(false);
        try {
            processorThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
