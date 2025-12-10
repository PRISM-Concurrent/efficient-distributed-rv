package phd.distributed.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@Tag("fast")
class SystemConfigTest {

    @Test
    void testTestModes() {
        assertEquals(SystemConfig.TestMode.FAST, SystemConfig.TEST_MODE);
        assertTrue(SystemConfig.getIterations() <= 100);
    }

    @Test
    void testFeatureFlags() {
        assertTrue(SystemConfig.FEATURES.asyncLogging);
        assertTrue(SystemConfig.FEATURES.parallelVerification);
        assertTrue(SystemConfig.FEATURES.smartPruning);
        assertTrue(SystemConfig.FEATURES.resultCaching);
        assertFalse(SystemConfig.FEATURES.objectPooling);
    }

    @Test
    void testFeatureFlagByName() {
        assertTrue(SystemConfig.FEATURES.isEnabled("async.logging"));
        assertTrue(SystemConfig.FEATURES.isEnabled("parallel.verification"));
        assertFalse(SystemConfig.FEATURES.isEnabled("unknown.feature"));
    }

    @Test
    void testSystemConfiguration() {
        assertTrue(SystemConfig.DEFAULT_THREAD_POOL_SIZE > 0);
        assertTrue(SystemConfig.DEFAULT_ITERATIONS > 0);
        assertTrue(SystemConfig.DEFAULT_TIMEOUT_MS > 0);
        assertTrue(SystemConfig.DEFAULT_BATCH_SIZE > 0);
    }

    @Test
    void testLoggingConfiguration() {
        assertTrue(SystemConfig.ASYNC_LOGGING_ENABLED);
        assertTrue(SystemConfig.USE_DISRUPTOR);
        assertTrue(SystemConfig.LOGGING_BUFFER_SIZE > 0);
        assertTrue(SystemConfig.EVENT_BUFFER_SIZE > 0);
    }
}
