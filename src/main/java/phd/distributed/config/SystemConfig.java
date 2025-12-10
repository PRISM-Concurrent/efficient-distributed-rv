package phd.distributed.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SystemConfig {
    private static final Properties config = new Properties();

    // System Configuration
    public static final int DEFAULT_THREAD_POOL_SIZE;
    public static final int DEFAULT_ITERATIONS;
    public static final long DEFAULT_TIMEOUT_MS;
    public static final int DEFAULT_BATCH_SIZE;

    // Logging Configuration
    public static final boolean ASYNC_LOGGING_ENABLED;
    public static final boolean USE_DISRUPTOR;
    public static final int LOGGING_BUFFER_SIZE;
    public static final int EVENT_BUFFER_SIZE;

    // Test Configuration
    public static final TestMode TEST_MODE;
    public static final int FAST_TEST_ITERATIONS;
    public static final int THOROUGH_TEST_ITERATIONS;
    public static final int STRESS_TEST_ITERATIONS;

    // Feature Flags
    public static final FeatureFlags FEATURES;

    // Performance Configuration
    public static final boolean PERFORMANCE_MONITORING_ENABLED;
    public static final boolean PERFORMANCE_PROFILING_ENABLED;

    static {
        loadConfiguration();

        DEFAULT_THREAD_POOL_SIZE = getInt("system.thread.pool.size", Runtime.getRuntime().availableProcessors());
        DEFAULT_ITERATIONS = getInt("system.default.iterations", 1000);
        DEFAULT_TIMEOUT_MS = getLong("system.default.timeout.ms", 30000L);
        DEFAULT_BATCH_SIZE = getInt("system.batch.size", 100);

        ASYNC_LOGGING_ENABLED = getBoolean("logging.async.enabled", true);
        USE_DISRUPTOR = getBoolean("logging.use.disruptor", true);
        LOGGING_BUFFER_SIZE = getInt("logging.buffer.size", 8192);
        EVENT_BUFFER_SIZE = getInt("logging.event.buffer.size", 16384);

        TEST_MODE = TestMode.fromString(getString("test.mode", "fast"));
        FAST_TEST_ITERATIONS = getInt("test.fast.iterations", 100);
        THOROUGH_TEST_ITERATIONS = getInt("test.thorough.iterations", 1000);
        STRESS_TEST_ITERATIONS = getInt("test.stress.iterations", 10000);

        FEATURES = new FeatureFlags();

        PERFORMANCE_MONITORING_ENABLED = getBoolean("performance.monitoring.enabled", false);
        PERFORMANCE_PROFILING_ENABLED = getBoolean("performance.profiling.enabled", false);
    }

    private static void loadConfiguration() {
        try (InputStream is = SystemConfig.class.getResourceAsStream("/system.properties")) {
            if (is != null) {
                config.load(is);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load system.properties, using defaults");
        }

        // Override with system properties
        config.putAll(System.getProperties());
    }

    private static String getString(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue) {
        String value = config.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static long getLong(String key, long defaultValue) {
        String value = config.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = config.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public static int getIterations() {
        return TEST_MODE.getIterations();
    }

    // Test Mode Enum
    public enum TestMode {
        FAST(100),
        THOROUGH(1000),
        STRESS(10000);

        private final int defaultIterations;

        TestMode(int defaultIterations) {
            this.defaultIterations = defaultIterations;
        }

        public int getIterations() {
            return switch (this) {
                case FAST -> FAST_TEST_ITERATIONS;
                case THOROUGH -> THOROUGH_TEST_ITERATIONS;
                case STRESS -> STRESS_TEST_ITERATIONS;
            };
        }

        public static TestMode fromString(String mode) {
            try {
                return TestMode.valueOf(mode.toUpperCase());
            } catch (IllegalArgumentException e) {
                return FAST;
            }
        }
    }

    // Feature Flags
    public static class FeatureFlags {
        public final boolean asyncLogging;
        public final boolean parallelVerification;
        public final boolean smartPruning;
        public final boolean resultCaching;
        public final boolean objectPooling;

        FeatureFlags() {
            this.asyncLogging = getBoolean("feature.async.logging", true);
            this.parallelVerification = getBoolean("feature.parallel.verification", false);
            this.smartPruning = getBoolean("feature.smart.pruning", false);
            this.resultCaching = getBoolean("feature.result.caching", false);
            this.objectPooling = getBoolean("feature.object.pooling", false);
        }

        public boolean isEnabled(String featureName) {
            return switch (featureName.toLowerCase()) {
                case "async.logging" -> asyncLogging;
                case "parallel.verification" -> parallelVerification;
                case "smart.pruning" -> smartPruning;
                case "result.caching" -> resultCaching;
                case "object.pooling" -> objectPooling;
                default -> false;
            };
        }
    }
}
