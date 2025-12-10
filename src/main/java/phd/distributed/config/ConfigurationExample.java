package phd.distributed.config;

/**
 * Example usage of SystemConfig and feature flags
 */
public class ConfigurationExample {

    public static void main(String[] args) {
        // Test Mode
        System.out.println("Test Mode: " + SystemConfig.TEST_MODE);
        System.out.println("Iterations: " + SystemConfig.getIterations());

        // Feature Flags
        System.out.println("\nFeature Flags:");
        System.out.println("  Async Logging: " + SystemConfig.FEATURES.asyncLogging);
        System.out.println("  Parallel Verification: " + SystemConfig.FEATURES.parallelVerification);
        System.out.println("  Smart Pruning: " + SystemConfig.FEATURES.smartPruning);
        System.out.println("  Result Caching: " + SystemConfig.FEATURES.resultCaching);
        System.out.println("  Object Pooling: " + SystemConfig.FEATURES.objectPooling);

        // System Configuration
        System.out.println("\nSystem Configuration:");
        System.out.println("  Thread Pool Size: " + SystemConfig.DEFAULT_THREAD_POOL_SIZE);
        System.out.println("  Default Iterations: " + SystemConfig.DEFAULT_ITERATIONS);
        System.out.println("  Timeout (ms): " + SystemConfig.DEFAULT_TIMEOUT_MS);
        System.out.println("  Batch Size: " + SystemConfig.DEFAULT_BATCH_SIZE);

        // Example: Using feature flags in code
        if (SystemConfig.FEATURES.parallelVerification) {
            System.out.println("\nUsing parallel verification");
        } else {
            System.out.println("\nUsing sequential verification");
        }

        // Example: Dynamic feature check
        if (SystemConfig.FEATURES.isEnabled("smart.pruning")) {
            System.out.println("Smart pruning enabled");
        }
    }
}
