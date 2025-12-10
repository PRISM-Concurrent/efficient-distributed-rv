package phd.distributed.verifier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import phd.distributed.config.SystemConfig;
import phd.distributed.datamodel.Event;
import phd.distributed.monitoring.PerformanceMetrics;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class VerificationCache {
    private final Cache<String, CachedResult> cache;
    private final PerformanceMetrics metrics = PerformanceMetrics.getInstance();

    public VerificationCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    }

    public Optional<CachedResult> get(List<Event> events) {
        if (!SystemConfig.FEATURES.resultCaching) {
            return Optional.empty();
        }

        String key = generateKey(events);
        CachedResult result = cache.getIfPresent(key);

        if (result != null) {
            metrics.incrementCounter("cache.hits");
        } else {
            metrics.incrementCounter("cache.misses");
        }

        return Optional.ofNullable(result);
    }

    public void put(List<Event> events, boolean result, long durationMs) {
        if (SystemConfig.FEATURES.resultCaching) {
            String key = generateKey(events);
            cache.put(key, new CachedResult(result, durationMs, System.currentTimeMillis()));
            metrics.incrementCounter("cache.puts");
        }
    }

    private String generateKey(List<Event> events) {
        return Integer.toString(events.hashCode());
    }

    public double getHitRate() {
        long hits = metrics.getCounter("cache.hits");
        long misses = metrics.getCounter("cache.misses");
        return hits + misses > 0 ? (double) hits / (hits + misses) : 0.0;
    }

    public record CachedResult(boolean passed, long durationMs, long timestamp) {}
}
