package phd.distributed.testing;

import phd.distributed.config.SystemConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestResultCache {
    private static final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private static final boolean ENABLED = SystemConfig.FEATURES.resultCaching;

    public static CachedResult get(String testSignature) {
        if (!ENABLED) return null;
        return cache.get(testSignature);
    }

    public static void put(String testSignature, boolean passed, long durationMs) {
        if (!ENABLED) return;
        cache.put(testSignature, new CachedResult(passed, durationMs, System.currentTimeMillis()));
    }

    public static void clear() {
        cache.clear();
    }

    public static int size() {
        return cache.size();
    }

    public static class CachedResult {
        public final boolean passed;
        public final long durationMs;
        public final long timestamp;

        CachedResult(boolean passed, long durationMs, long timestamp) {
            this.passed = passed;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
        }

        public boolean isStale(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }
}
