package phd.distributed.monitoring;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class PerformanceMetrics {
    private static final PerformanceMetrics INSTANCE = new PerformanceMetrics();

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> timers = new ConcurrentHashMap<>();

    public static PerformanceMetrics getInstance() {
        return INSTANCE;
    }

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public void recordTime(String name, long nanos) {
        timers.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(nanos);
    }

    public long getCounter(String name) {
        LongAdder counter = counters.get(name);
        return counter != null ? counter.sum() : 0;
    }

    public long getTime(String name) {
        AtomicLong timer = timers.get(name);
        return timer != null ? timer.get() : 0;
    }

    public void reset() {
        counters.clear();
        timers.clear();
    }

    public String report() {
        StringBuilder sb = new StringBuilder("Performance Metrics:\n");
        counters.forEach((name, value) ->
            sb.append(String.format("  %s: %d%n", name, value.sum())));
        timers.forEach((name, value) ->
            sb.append(String.format("  %s: %.2f ms%n", name, value.get() / 1_000_000.0)));
        return sb.toString();
    }
}
