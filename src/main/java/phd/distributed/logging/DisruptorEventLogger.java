package phd.distributed.logging;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import phd.distributed.datamodel.Event;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DisruptorEventLogger implements EventLogger {
    private static final Logger EVENT_LOGGER = LogManager.getLogger("phd.distributed.events");
    private static final int BUFFER_SIZE = 65536; // Must be power of 2

    private final Disruptor<LogEvent> disruptor;
    private final RingBuffer<LogEvent> ringBuffer;

    private static final DisruptorEventLogger INSTANCE = new DisruptorEventLogger();

    private DisruptorEventLogger() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "DisruptorEventLogger-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };

        disruptor = new Disruptor<>(
            LogEvent::new,
            BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI,
            new BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(new EventLogHandler());
        disruptor.start();

        ringBuffer = disruptor.getRingBuffer();
    }

    public static DisruptorEventLogger getInstance() {
        return INSTANCE;
    }

    public void logEvent(Event event) {
        ringBuffer.publishEvent((logEvent, sequence, arg) -> {
            logEvent.threadId = arg.getId();
            logEvent.operation = arg.getEvent();
            logEvent.counter = arg.getCounter();
            logEvent.timestamp = System.nanoTime();
        }, event);
    }

    public void shutdown() {
        disruptor.shutdown();
    }

    static class LogEvent {
        int threadId;
        Object operation;
        int counter;
        long timestamp;
    }

    static class EventLogHandler implements EventHandler<LogEvent> {
        private static final int BATCH_SIZE = 100;
        private final StringBuilder batchBuffer = new StringBuilder(BATCH_SIZE * 100);
        private int batchCount = 0;

        @Override
        public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
            batchBuffer.append("Thread[").append(event.threadId)
                      .append("] Op[").append(event.operation)
                      .append("] Counter[").append(event.counter)
                      .append("]\n");
            batchCount++;

            if (endOfBatch || batchCount >= BATCH_SIZE) {
                flush();
            }
        }

        private void flush() {
            if (batchCount > 0) {
                EVENT_LOGGER.info("Batch[{}]:\n{}", batchCount, batchBuffer.toString());
                batchBuffer.setLength(0);
                batchCount = 0;
            }
        }
    }
}
